;; Functions for generating random items.
(ns robinson.crafting
  (:require [robinson.common :as rc]
            [taoensso.timbre :as log]
            [robinson.world :as rw]
            [robinson.itemgen :as ig]
            [robinson.player :as rp]
            [loom.graph :as lg]
            [loom.label :as ll]
            [datascript.core :as d]))

(defprotocol Mod
  (mod-name [this])
  (mod-type [this])
  (mod-apply [this item]))

(defn current-recipe [state]
  (let [recipe-type (get-in state [:world :in-progress-recipe-type])]
    (get-in state [:world :in-progress-recipes recipe-type])))

(defn assoc-current-recipe [state & kvs]
  {:pre [(not (nil? state))]
   :post [(not (nil? %))]}
  (let [recipe-type (get-in state [:world :in-progress-recipe-type])]
    (update-in state [:world :in-progress-recipes recipe-type]
      (fn [recipe] (apply assoc recipe kvs)))))

(defn update-current-recipe [state f & xs]
  {:pre [(not (nil? state))]
   :post [(not (nil? %))]}
  (let [recipe-type (get-in state [:world :in-progress-recipe-type])]
    (update-in state [:world :in-progress-recipes recipe-type]
      (fn [recipe] (apply f recipe xs)))))

(def recipe-schema {
  :recipe/id {:db/unique :db.unique/identity}
  #_#_:recipe/components {:db/cardinality :db.cardinality/many}
  :recipe/types {:db/cardinality :db.cardinality/many}})

(defn low-weight [item]
  (< (get item :weight 0) 1))

(defn stick-like [item]
  (contains? (get item :properties) :stick-like))

(defn rock [item]
  (= (get :item/id item) :rock))

(defn flexible [item]
  (contains? (get item :properties) :flexible))

(defn tensile [item]
  (< 1 (get item :tensile-strength 0)))

(defn planar [item]
  (contains? (get item :properties) :planar))

(defn pointed [item]
  (< (get item :roundness 1) 1))

(defn sharp [item]
  (< 1 (get item :sharpness)))

(defn edged [item]
  (contains? (get item :properties) :edged))

(defn round [item]
  (< (get item :roundness 1) 1))

(def recipes [
  ;; weapons
     ; blunt
     {:recipe/id  :club
      :recipe/category :weapon
      :recipe/types #{:blunt :melee}
      :recipe/requirements '[and [low-weight]
                                 [stick-like]]}
     {:recipe/id  :rock
      :recipe/category :weapon
      :recipe/types #{:blunt :thrown}
      :recipe/requirements '[and [low-weight]
                                  [rock]]}
     {:recipe/id  :sling
      :recipe/category :weapon
      :recipe/types #{:blunt :ranged}
      :recipe/requirements '[and [flexible]
                                 [and [tensile]
                                      [planar]]]}
     ; edged
     {:recipe/id  :dagger
      :recipe/category :weapon
      :recipe/types #{:edged :melee}
      :recipe/requirements '[and [edged]
                                 [and [low-weight]
                                      [stick-like]]]}
     {:recipe/id  :throwing-axe
      :recipe/category :weapon
      :recipe/types #{:edged :thrown}
      :recipe/requirements '[and [edged]
                                 [and [low-weight]
                                      [stick-like]]]}
      {:recipe/id  :boomarang
      :recipe/category :weapon
      :recipe/types #{:edged :ranged}
      :recipe/requirements '[and [low-weight]
                                 [planar]]}
     ; piercing
     {:recipe/id  :spear
      :recipe/category :weapon
      :recipe/types #{:piercing :melee}
      :recipe/requirements '[and [pointed]
                                 [and [low-weight]
                                      [stick-like]]]}
     {:recipe/id  :throwing-spear
      :recipe/category :weapon
      :recipe/types #{:piercing :thrown}
      :recipe/requirements '[and [sharp]
                                 [and [low-weight]
                                      [stick-like]]]}
     {:recipe/id  :bow
      :recipe/category :weapon
      :recipe/types #{:piercing :ranged}
      :recipe/requirements '[and [flexible]
                                 [and [low-weight]
                                       [stick-like]]]}
     {:recipe/id  :blowgun
      :recipe/category :weapon
      :recipe/types #{:piercing :ranged}
      :recipe/requirements '[and [tube-like]
                                 [low-weight stick-like]]}
      ; flexible
     {:recipe/id  :garrote
      :recipe/category :weapon
      :recipe/types #{:flexible :melee}
      :recipe/requirements '[and [flexible]
                                 [low-weight stick-like]]}
     {:recipe/id  :bolas
      :recipe/category :weapon
      :recipe/types #{:flexible :thrown}
      :recipe/requirements '[and [flexible]
                                 [count 3 [round low-weight]]]}
                             
     {:recipe/id  :whip
      :recipe/category :weapon
      :recipe/types #{:flexible :ranged}
      :recipe/requirements '[flexible]}])

(comment :weapons  [
     {:name "flint spear"            :hotkey \a :hunger 10 :thirst 20 :recipe {:exhaust [:flint-blade :stick :rope] :add [:flint-spear]}}
     {:name "flint axe"              :hotkey \b :hunger 10 :thirst 20 :recipe {:exhaust [:flint-axe-blade :stick :rope] :add [:flint-axe]}}
     {:name "flint knife"            :hotkey \c :hunger 10 :thirst 20 :recipe {:exhaust [:flint-blade :stick :rope] :add [:flint-knife]}}
     {:name "obsidian spear"         :hotkey \d :hunger 10 :thirst 20 :recipe {:exhaust [:obsidian-blade :stick :rope] :add [:obsidian-spear]}}
     {:name "obsidian axe"           :hotkey \e :hunger 10 :thirst 20 :recipe {:exhaust [:obsidian-blade :stick :rope] :add [:obsidian-axe]}}
     {:name "obsidian knife"         :hotkey \f :hunger 10 :thirst 20 :recipe {:exhaust [:obsidian-blade :stick :rope] :add [:obsidian-knife]}}
     {:name "bow"                    :hotkey \g :hunger 10 :thirst 20 :recipe {:exhaust [:stick :rope] :add [:bow]}}
     {:name "arrow"                  :hotkey \h :hunger 10 :thirst 20 :recipe {:exhaust [:obsidian-blade :stick] :add [:arrow]}}]
   :survival [
     {:name "flint blade"            :hotkey \a :hunger 10 :thirst 20 :recipe {:exhaust [:rock :flint]                 :add [:flint-blade]}}
     {:name "flint axe blade"        :hotkey \b :hunger 10 :thirst 20 :recipe {:exhaust [:rock :large-flint]           :add [:flint-axe-blade]}}
     {:name "obsidian blade"         :hotkey \c :hunger 10 :thirst 20 :recipe {:exhaust [:rock :obsidian]              :add [:obsidian-blade]}}
     {:name "rope"                   :hotkey \d :hunger 10 :thirst 20 :recipe {:exhaust [:plant-fiber]                 :add [:rope]}}
     {:name "sharpened stick"        :hotkey \e :hunger 10 :thirst 20 :recipe {:exhaust [:stick]
                                                                               :have-or [:obsidian-knife
                                                                                         :obsidian-spear
                                                                                         :obsidian-axe
                                                                                         :knife]
                                                                               :add     [:sharpened-stick]}}
     {:name "bamboo water collector" :hotkey \f :hunger 10 :thirst 20 :recipe {:exhaust [:rope :bamboo :stick]
                                                                               :have-or [:obsidian-knife
                                                                                         :obsidian-spear
                                                                                         :obsidian-axe
                                                                                         :knife]
                                                                               :add [:bamboo-water-collector]} :place :cell-type}
     {:name "solar still"            :hotkey \g :hunger 10 :thirst 20 :recipe {:exhaust [:rock :tarp :stick :coconut-shell]
                                                                               :have-or [:stick]
                                                                               :add [:solar-still]} :place :cell-type}
     {:name "fishing pole"           :hotkey \h :hunger 10 :thirst 20 :recipe {:exhaust [:fishing-line-and-hook :stick]
                                                                               :add [:fishing-pole]}
                                                                      :place :inventory}
     {:name "fire plough"            :hotkey \i :hunger 10 :thirst 20 :recipe {:exhaust [:stick :stick]
                                                                               :add [:fire-plough]}
                                                                      :place :inventory}
     {:name "hand drill"             :hotkey \j :hunger 10 :thirst 20 :recipe {:exhaust [:stick :stick]
                                                                               :add [:hand-drill]}
                                                                      :place :inventory}
     {:name "bow drill"              :hotkey \k :hunger 10 :thirst 20 :recipe {:exhaust [:stick :stick :stick :rope :rock]
                                                                               :add [:bow-drill]}
                                                                      :place :inventory}
     {:name "campfire"               :hotkey \l :hunger 10 :thirst 20 :recipe {:exhaust [:match :stick :log :log :rock :rock :rock]
                                                                               :add [:campfire]}
                                                                      :place :cell-type}]
   :shelter [
     {:name "palisade"               :hotkey \a :hunger 10 :thirst 20 :recipe {:exhaust [:rope :sharpened-stick]       :add [:palisade]} :place :inventory}
     {:name "ramada"                 :hotkey \b :hunger 10 :thirst 20 :recipe {:exhaust [:rope :leaves :stick
                                                                                         :stick :stick :stick :stick]  :add [:ramada]}  :place :cell-type}
     {:name "tarp shelter"           :hotkey \c :hunger 10 :thirst 20 :recipe {:exhaust [:rope :tarp :stick
                                                                                         :stick :stick :stick]         :add [:tarp-shelter]}  :place :cell-type}
     {:name "lean-to"                :hotkey \d :hunger 10 :thirst 20 :recipe {:exhaust [:leaves :stick :stick
                                                                                         :stick :stick :stick]         :add [:lean-to]}  :place :cell-type}]
   :traps [
     {:name "snare"                  :hotkey \a :hunger 10 :thirst 20 :recipe {:exhaust [:rope :stick]                 :add [:snare]}}
     {:name "deadfall trap"          :hotkey \b :hunger 10 :thirst 20 :recipe {:exhaust [:rope :stick :rock]           :add [:deadfall-trap]}}]
   :transportation [
     {:name "raft"                   :hotkey \a :hunger 10 :thirst 20 :recipe {:exhaust [:rope :log :log
                                                                                         :log :log :log]
                                                                                   :add [:raft]} :place :drop}])

(def recipe-db
  (-> (d/empty-db recipe-schema)
      (d/db-with recipes)))

(defn get-recipe [id]
  (ffirst
    (d/q '[:find (pull ?e [*])
           :in $ ?id
           :where
           [?e :recipe/id ?id]]
            recipe-db
            id)))

(defn get-recipe-by-types [types]
  (let [rules '[[(matches-all ?info ?seq ?first ?rest ?empty ?e ?a ?vs)
                 [(?seq ?vs)]
                 [(?first ?vs) ?v]
                 [?e ?a ?v]
                 [?e :recipe/id ?recipe-id]
                 [(?rest ?vs) ?vs-rest]
                 (matches-all ?info ?seq ?first ?rest ?empty ?e ?a ?vs-rest)]
                [(matches-all ?info ?seq ?first ?rest ?empty ?e ?a ?vs)
                 [?e :recipe/id ?recipe-id]
                 [(?empty ?vs)]]]]
  (ffirst
    (d/q '[:find (pull ?e [*])
           :in $ % ?info ?seq ?first ?rest ?empty ?types
           :where
           ;[(?info ?types)]
           ;[?e :recipe/id :club]
           (matches-all ?info ?seq ?first ?rest ?empty ?e :recipe/types ?types)]
          recipe-db
          rules
          (fn [msg x] (log/info msg x) true)
          seq
          first
          rest
          empty?
          #{:blunt :melee}
          types))))

(defn satisfies-helper?
  [l expr]
  "Counts the items in that 'satisfy' the expression.
   Results >= 1 indicate satisfaction. Results < 1
   indicate that the expression is unsatisfied."
  (cond
    (nil? expr)
      (assert false (str "Invalid expression" expr))
    (= (first expr) 'count)
      (let [[_ n arg] expr]
        ;(println "count" n arg)
        (/ (satisfies-helper? l arg) n))
    (= (first expr) 'and)
      (let [[_ & args] expr]
        ;(println "and" args)
        (reduce min (map (partial satisfies-helper? l) args)))
    (= (first expr) 'or)
      (let [[_ & args] expr]
        ;(println "or" args)
        (reduce + (map (partial satisfies-helper? l) args)))
    (keyword? (first expr))
      (let [[arg] expr]
        ;(println "keyword" arg expr)
        (->> l
           (map :item/id)
           (filter (partial = arg))
           (map count)
           (reduce + 0)))
    (symbol? (first expr))
      ; when multiple symbols are present, use the conjunction of all of them s0 and s1 and .... and sn
      (let [preds-juxt (apply juxt (map (partial ns-resolve 'robinson.crafting expr) expr))
            and-comp (fn [e] (every? identity (preds-juxt e)))]
        (->> l
          (filter and-comp)
          count))
    :else
      (assert false (str "Invalid expression" expr))))
  
(defn has-prerequisites?
  "Return true if the player has the ability to make the recipe."
  [state recipe]
  (let [inventory        (get-in state [:world :player :inventory])
        requirements     (get recipe :recipe/requirements)]
    (<= 1 (satisfies-helper? inventory requirements))))

(defn get-recipes
  "Return recipes tagged with :applicable true if the recipe has the required pre-requisites."
  [state]
  (apply hash-map
    (mapcat
      (fn [[group-name group]]
        [group-name (map (fn [recipe] (if (has-prerequisites? state recipe)
                                        (assoc recipe :applicable true)
                                        recipe))
                         group)])
      (get-in state [:world :player :recipes]))))

(defn get-recipes-by-category
  "Return recipes tagged with :applicable true if the recipe has the required pre-requisites."
  [state recipe-category]
  (map
    (fn [recipe] (if (has-prerequisites? state recipe)
                                      (assoc recipe :applicable true)
                                      recipe))
    (get-in state [:world :player :recipes recipe-category])))

(defn- exhaust-by-ids
  [state ids]
  (reduce (fn [state id]
            (do 
              (log/info "removing" id)
              (rp/dec-item-count state id)))
          state
          ids))

(defn- place-cell-type
  [state id]
    (let [[x y] (rp/player-xy state)]
      (rw/assoc-cell state x y :type id)))
  
(defn- place-drop
  [state id]
  (let [[x y] (rp/player-xy state)]
    (rw/conj-cell-items state x y (ig/id->item id))))
  
(defn- add-by-ids
  [state ids place]
  (reduce (fn [state id]
            (case place
              :cell-type
                (place-cell-type state id)
              :drop
                (place-drop state id)
              :inventory
                (let [item (ig/id->item id)]
                  (log/info "adding" item)
                  (rp/add-to-inventory state [item]))))
          state
          ids))
  
; Recipe node naviation
(defn next-nodes
  [recipe]
  (let [graph (get recipe :graph)
        current-node (get recipe :current-node)]
    (lg/successors graph current-node)))

(defn next-node-choices [recipe]
  (let [graph (get recipe :graph)
        current-node (get recipe :current-node)
        current-node-x (-> graph
                         (ll/label current-node)
                         :x)
        next-nodes (next-nodes recipe)]
    (if (seq next-nodes)
      (map (fn [n]
             (let [x (-> graph (ll/label n) :x)]
               (cond
                 (< x current-node-x)
                   {:name "left"
                    :hotkey \l
                    :next-node n}
                 (= x current-node-x)
                   {:name "down"
                    :hotkey \d
                    :next-node n}
                 (> x current-node-x)
                   {:name "right"
                    :hotkey \r
                    :next-node n})))
             next-nodes)
      [{:name "finish recipe"
        :hotkey :space
        :done true}])))

(defn recipe-name
  [recipe]
  (let [item-name (-> recipe
                    :types
                    get-recipe-by-types
                    :recipe/id
                    ig/id->name)]
    ;(apply str item-name (map mod-name (get recipe :effects)))
    ;(log/info (-> recipe :types))
    ;(-> recipe :types)
    ;(assert (some item-name) (str "item-name not found" item-name))
    item-name))

(defn save-recipe [state]
  (log/info "Saving recipe")
  (let [recipe (current-recipe state)
        selected-recipe-hotkey (get-in state [:world :selected-recipe-hotkey])]
    (-> state
      (update-in [:world :player :recipes] assoc
        selected-recipe-hotkey (assoc recipe :name (recipe-name recipe)))
      (rw/assoc-current-state :recipes))))

(defn fill-event
  [event]
  (if
    ; next-event has choices,
    (seq (get event :choices))
      event
    ; next event has no choices
    (assoc event :choices [{
      :hotkey :space
      :name "continue"}])))

(defn fill-choice
  [add-done choice]
  (if add-done
    (assoc choice :done true)
    choice))

; Input handlers
(defn resolve-choice [state recipe-ns recipe keyin]
  (let [current-stage (get recipe :current-stage)]
        ; find selected choice
    (if-let [choice (->> (get current-stage :choices)
                      (filter #(= (get % :hotkey) keyin))
                      first)]
      (let [results (select-keys choice [:types :effects :materials])
            ; merge results into current recipe
            state-with-results (update-current-recipe state (partial merge-with into) results)]
        ; done with recipe?
        (if (contains? choice :done)
          (save-recipe state-with-results)
          ;; either a regular event, or a direction event
          ; if choice has a events pick one,
          (if (seq (get choice :events))
            ; find next event
            (let [next-event (rand-nth (get choice :events))
                  ; fill in event defaults
                  next-event (fill-event next-event)]
              ; assign next event and return
              (assoc-current-recipe
                state-with-results
                :current-stage next-event))
            ; else the choice has no events, then the next step is to move to the next node
            (if-let [next-node (get choice :next-node)]
              ; choice points to next-node
              ; gen event for next node and advance to it
              ; typical direction choice
              (->
                (let [next-node-type (get (ll/label (get recipe :graph) next-node) :type)]
                  ((case next-node-type
                    \? (ns-resolve recipe-ns 'gen-question)
                    \! (ns-resolve recipe-ns 'gen-complication)
                    \+ (ns-resolve recipe-ns 'gen-remedy)
                    \& (ns-resolve recipe-ns 'gen-material)
                    \☼ (ns-resolve recipe-ns 'gen-enhancement)
                    (assert false (str "next node type unknown " next-node next-node-type)))
                    state-with-results (current-recipe state-with-results)))
                (assoc-current-recipe :current-node next-node))
              ; choice does not point to next node
              (let [next-node-choices (next-node-choices recipe)]
                (log/info "NO NEXT NODE in CHOICE")
                (log/info "choice" choice)
                (log/info "next-node-choices" (vec next-node-choices))
                (cond
                  ; no more nodes, create finish recipe event
                  (not (seq (next-nodes recipe)))
                    (assoc-current-recipe state-with-results
                      :current-stage
                      {:title "Done"
                       :choices [
                          {:name "finish recipe"
                           :hotkey :space
                           :done true}]})
                  ; one path to next node? auto-increment
                  (= 1 (count next-node-choices))
                    (->
                      (let [next-node (-> next-node-choices first :next-node)
                            _ (log/info "next-node" next-node)
                            next-node-type (get (ll/label (get recipe :graph)
                                                          next-node)
                                                :type)]
 
                        ((case next-node-type
                          \? (ns-resolve recipe-ns 'gen-question)
                          \! (ns-resolve recipe-ns 'gen-complication)
                          \+ (ns-resolve recipe-ns 'gen-remedy)
                          \& (ns-resolve recipe-ns 'gen-material)
                          \☼ (ns-resolve recipe-ns 'gen-enhancement)
                          (assert false (str "next node type unknown " next-node next-node-type)))
                          state-with-results (current-recipe state-with-results)))
                      (assoc-current-recipe :current-node (-> next-node-choices first :next-node)))
                  ;; else multiple choices to next node, create event to choose
                  :else
                    (assoc-current-recipe
                      state-with-results
                      :current-stage
                      {:title "Choose path"
                       :choices next-node-choices}))))))))))

(defn update [state recipe-ns keyin]
  (let [recipe-type (get-in state [:world :in-progress-recipe-type])
        recipe (get-in state [:world :in-progress-recipes recipe-type])]
    (resolve-choice state recipe-ns recipe keyin)))

(defn init [state recipe-ns recipe]
  {:pre [(not (nil? state))]
   :post [(not (nil? %))]}
  (let [n (get recipe :current-node)]
    (log/info "current node label" (ll/label (get recipe :graph) n))
    ((case (get (ll/label (get recipe :graph) n) :type)
        \? (ns-resolve recipe-ns 'gen-question)
        (assert false (str "current node not question" n (get recipe :graph))))
       state recipe)))

(defn craft-recipe
  "Perform the recipe."
  [state recipe]
  (let [exhaust      (get-in recipe [:recipe :exhaust])
        add          (get-in recipe [:recipe :add])
        have-or      (get-in recipe [:recipe :have-or])
        hunger       (get recipe :hunger)
        thirst       (get recipe :thirst)
        wielded-item (rp/wielded-item (rp/player-inventory state))
        have-applicable-ids (clojure.set/intersection (set have-or)
                                                       (set (map :id (rp/player-inventory state))))
        _ (log/info "crafting" recipe)]
    (if (has-prerequisites? state recipe)
      ;; player has only one applicable item, or has many and is wielding one, or the recipe doesn't require the player to have any items?
      (if (or (zero? (count have-or))
              (= (count have-applicable-ids) 1)
              wielded-item)
        (let [state (as-> state state
                      (if (or (= (count have-applicable-ids) 1)
                              wielded-item)
                        (rp/dec-item-utility state (or (and wielded-item (get wielded-item :id))
                                                       (first have-applicable-ids)))
                        state)
                      (add-by-ids state add (get recipe :place :inventory))
                      (exhaust-by-ids state exhaust)
                      (rp/player-update-hunger state (fn [current-hunger] (min (+ hunger current-hunger)
                                                                               (rp/player-max-hunger state))))
                      (rp/player-update-thirst state (fn [current-thirst] (min (+ hunger current-thirst)
                                                                               (rp/player-max-thirst state))))
                      (reduce rp/update-crafted state (map (fn [id] {:id id}) add)))]
          state)
        (rc/ui-hint state (format "You have multiple items that can be used to make this. Wield one of them")))
      (rc/ui-hint state (format "You don't have the necessary items to make %s recipe." (get recipe :name))))))

(defn player-recipes [state]
  (let [empty-recipe {:name "Empty" :alt "----" :empty true}]
    (map (fn [[hotkey recipe]]
           (assoc recipe :hotkey hotkey :name (if (get recipe :empty)
                                                "Empty"
                                                (recipe-name recipe))))
         (merge
           {\a empty-recipe
            \b empty-recipe
            \c empty-recipe}
           (get-in state [:world :player :recipes] {})))))
