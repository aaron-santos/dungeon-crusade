;; Functions for manipulating player state
(ns robinson.player
  (:use     robinson.common)
  (:require [taoensso.timbre :as timbre]
            [pallet.thread-expr :as tx]))

(timbre/refer-timbre)

(defn player-dead?
  "Return `true` if the player has a status of `:dead`."
  [state]
  (contains? (-> state :world :player :status) :dead))

(defn- merge-items
  [item1 item2]
  ;(info "merging" item1 item2)
  (cond 
    (and (not (contains? item1 :count))
         (not (contains? item2 :count)))
      (assoc item1 :count 2)
    (and (contains? item1 :count)
         (not (contains? item2 :count)))
      (update-in item1 [:count] inc)
    (and (not (contains? item1 :count))
         (contains? item2 :count))
      (update-in item2 [:count] inc)
    (and (contains? item1 :count)
         (contains? item2 :count))
      (update-in item1 [:count] (partial + (get item2 :count)))))

(defn add-to-inventory
  "Adds `item` to player's inventory assigning hotkeys as necessary."
  [state items]
  (let [inventory               (get-in state [:world :player :inventory])
        remaining-hotkeys       (get-in state [:world :remaining-hotkeys])
        original-remaining-hotkeys remaining-hotkeys
        inventory-hotkeys       (set (map :hotkey inventory))
        ;; find hotkeys of all items we're adding to inventory
        item-hotkeys            (set (remove nil? (map :hotkey items)))
        _                       (trace remaining-hotkeys items)
        _                       (trace "inventory hotkeys" (set (map :hotkey inventory)))
        _                       (trace "item hotkeys" (set (map :hotkey items)))
        inventory               (mapv
                                  (fn [items]
                                    (reduce merge-items items))
                                  (vals (group-by :id  (concat inventory items))))
        _                       (trace "new inventory hotkeys" (set (map :hotkey inventory)))
        ;; find the hotkeys that were previously used in inventory that are no longer in use
        freed-inventory-hotkeys (clojure.set/difference inventory-hotkeys (set (map :hotkey inventory)))
        ;; find the hotkeys that were used in the added items that are no longer in use
        freed-item-hotkeys      (clojure.set/difference item-hotkeys (set (map :hotkey inventory)))
        _                       (trace "freed-hotkeys" (clojure.set/union freed-inventory-hotkeys freed-item-hotkeys))
        ;; find all the free hotkeys that were the previous free hotkeys plus the newly freed item and inventory hotkeys.
        remaining-hotkeys       (vec (sort (vec (clojure.set/union remaining-hotkeys freed-item-hotkeys freed-inventory-hotkeys))))
        _                       (trace "remaining-hotkeys" remaining-hotkeys)
        inventory               (vec (fill-missing #(not (contains? % :hotkey))
                                                   #(assoc %1 :hotkey %2)
                                                   remaining-hotkeys
                                                   inventory))
        ;; find all the free hotkeys after filling in missing hotkeys into the newly added inventory items
        remaining-hotkeys       (vec (sort (vec (clojure.set/difference (set remaining-hotkeys) (set (map :hotkey inventory))))))
        newly-assigned-hotkeys  (vec (sort (vec (clojure.set/difference (set original-remaining-hotkeys) (set remaining-hotkeys)))))
        _                       (info "newly assigned hotkeys" newly-assigned-hotkeys)]
    (-> state
      ;; TODO: append log with message about new items and their hotkeys
      (assoc-in [:world :player :inventory] inventory)
      (assoc-in [:world :remaining-hotkeys] (vec remaining-hotkeys))
      ((fn [state] (reduce (fn [state item] (let [item (first (filter (fn [i] (= (get i :id) (get item :id)))
                                                                      (get-in state [:world :player :inventory])))]
                                               (append-log state (format "%s-%c" (get item :name) (get item :hotkey)))))
                           state
                           items))))))
        
(defn remove-from-inventory
  "Removes item with `id` from player's inventory freeing hotkeys as necessary."
  [state id]
  (let [item   (first (filter (fn [item] (= (get item :id) id)) (get-in state [:world :player :inventory])))
        hotkey (get item :hotkey)
        _ (info "removing item" item)
        _ (info "freeing hotkey" hotkey)]
    (-> state
      (update-in [:world :player :inventory] (log-io "inventory io" (fn [inventory]
                                                                      (vec (remove-first (fn [item] (= (get item :id) id))
                                                                                         inventory)))))
      (conj-in [:world :remaining-hotkeys] hotkey))))
