(ns entity.stats
  (:require [clojure.string :as str]
            [clojure.math :as math]
            [malli.core :as m]
            [gdx.graphics.color :as color]
            [utils.random :as random]
            [data.val-max :refer [val-max-schema val-max-ratio lower-than-max? set-to-max]]
            [core.component :refer [defsystem defcomponent]]
            [core.data :as data]
            [api.effect :as effect]
            [api.entity :as entity]
            [api.graphics :as g]
            [api.tx :refer [transact!]]
            [context.ui.config :refer (hpbar-height-px)]))

(defn- conj-value [value]
  (fn [values]
    (conj values value)))

(defn- remove-value [value]
  (fn [values]
    {:post [(= (count %) (dec (count values)))]}
    (vec (remove #{value} values)))) ; vec so can inspect and not 'lazy-seq'

(defn- txs-update-modifiers [entity modifiers f]
  (for [[modifier-k operations] modifiers
        [operation-k value] operations]
    [:tx.entity/update-in entity [:entity/stats :stats/modifiers modifier-k operation-k] (f value)]))

(comment
 (= (txs-update-modifiers :entity
                         {:modifier/hp {:op/max-inc 5
                                        :op/max-mult 0.3}
                          :modifier/movement-speed {:op/mult 0.1}}
                         (fn [value] :fn))
    [[:tx.entity/update-in :entity [:entity/stats :stats/modifiers :modifier/hp :op/max-inc] :fn]
     [:tx.entity/update-in :entity [:entity/stats :stats/modifiers :modifier/hp :op/max-mult] :fn]
     [:tx.entity/update-in :entity [:entity/stats :stats/modifiers :modifier/movement-speed :op/mult] :fn]])
 )

(defmethod transact! :tx/apply-modifiers [[_ entity modifiers] _ctx]
  (txs-update-modifiers entity modifiers conj-value))

(defmethod transact! :tx/reverse-modifiers [[_ entity modifiers] _ctx]
  (txs-update-modifiers entity modifiers remove-value))

(defn- ->pos-int [v]
  (-> v int (max 0)))

(defn- val-max-op-k->parts [op-k]
  (mapv keyword (str/split (name op-k) #"-")))

(comment
 (= (val-max-op-k->parts :op/val-inc) [:val :inc])
 )

(defsystem operation-text [_])
(defsystem apply-operation [_ base-value])
(defsystem operation-order [_])

(defn- apply-val-max-operation [[operation-k value] val-max]
  {:post [(m/validate val-max-schema %)]}
  (assert (m/validate val-max-schema val-max)
          (str "Invalid val-max-schema: " (pr-str val-max)))
  (let [[val-or-max inc-or-mult] (val-max-op-k->parts operation-k)
        f #(apply-operation [(keyword "op" (name inc-or-mult)) value] %)
        [v mx] (update val-max (case val-or-max :val 0 :max 1) f)
        v  (->pos-int v)
        mx (->pos-int mx)]
    (case val-or-max
      :val [v (max v mx)]
      :max [(min v mx) mx])))

(defn- ->percent [v]
  (str (int (* 100 v)) "%"))

(defcomponent :op/inc {:widget :text-field :schema number?} ; TODO for strength its only int increase, for movement-speed different .... ??? how to manage this ?
  (operation-text [[_ value]] (str value " "))
  (apply-operation [[_ value] base-value] (+ base-value value))
  (operation-order [_] 0))

(defcomponent :op/mult {:widget :text-field :schema number?}
  (operation-text [[_ value]] (str (->percent value) " "))
  (apply-operation [[_ value] base-value] (* base-value (inc value)))
  (operation-order [_] 1))

(defcomponent :op/val-inc {:widget :text-field :schema int?}
  (operation-text [[_ value]] (str value " minimum "))
  (apply-operation [this base-value] (apply-val-max-operation this base-value))
  (operation-order [_] 0))

(defcomponent :op/val-mult {:widget :text-field :schema number?}
  (operation-text [[_ value]] (str (->percent value) " minimum "))
  (apply-operation [this base-value] (apply-val-max-operation this base-value))
  (operation-order [_] 1))

(defcomponent :op/max-inc {:widget :text-field :schema int?}
  (operation-text [[_ value]] (str value " maximum "))
  (apply-operation [this base-value] (apply-val-max-operation this base-value))
  (operation-order [_] 0))

(defcomponent :op/max-mult {:widget :text-field :schema number?}
  (operation-text [[_ value]] (str (->percent value) " maximum "))
  (apply-operation [this base-value] (apply-val-max-operation this base-value))
  (operation-order [_] 1))

(comment
 (and
  (= (apply-operation [:op/val-inc 30]    [5 10]) [35 35])
  (= (apply-operation [:op/max-mult -0.5] [5 10]) [5 5])
  (= (apply-operation [:op/val-mult 2]    [5 10]) [15 15])
  (= (apply-operation [:op/val-mult 1.3]  [5 10]) [11 11])
  (= (apply-operation [:op/max-mult -0.8] [5 10]) [1 1])
  (= (apply-operation [:op/max-mult -0.9] [5 10]) [0 0]))
 )

; For now no green/red color for positive/negative numbers
; as :stats/damage-receive negative value would be red but actually a useful buff
(def ^:private positive-modifier-color "[CYAN]" #_"[LIME]")
(def ^:private negative-modifier-color "[CYAN]" #_"[SCARLET]")

(defn- +? [n]
  (case (math/signum n)
    (0.0 1.0) (str positive-modifier-color "+")
    -1.0 (str negative-modifier-color "")))

(defn- k->pretty-name [k]
  (str/capitalize (name k)))

(defn info-text [modifier-k [operation-k value]]
  (str (+? value)
       (operation-text [operation-k value])
       (k->pretty-name modifier-k)
       "[]"))

; TODO
; * bounds (action-speed not <=0 , not value '-1' e.g.)/schema/values/allowed operations
; * take max-movement-speed into account @ :stats/movement-speed
(defn- ->effective-value [base-value modifier-k stats]
  {:pre [(= "modifier" (namespace modifier-k))]}
  (->> stats
       :stats/modifiers
       modifier-k
       (sort-by operation-order)
       (reduce (fn [base-value [operation-k values]]
                 (apply-operation [operation-k (apply + values)] base-value))
               base-value)))

(comment
 (and
  (= (->effective-value [5 10]
                        :modifier/damage-deal
                        {:stats/modifiers {:modifier/damage-deal {:op/val-inc [30]}
                                           :stats/fooz-barz {:op/babu [1 2 3]}}})
     [35 35])
  (= (->effective-value [5 10]
                        :modifier/damage-deal
                        {:stats/modifiers {}})
     [5 10])
  (= (->effective-value [100 100]
                        :stats/hp
                        {:stats/modifiers {:stats/hp {:op/max-inc [10 1]
                                                      :op/max-mult [0.5]}}})
     [100 166])
  (= (->effective-value 3
                        :stats/movement-speed
                        {:stats/modifiers {:stats/movement-speed {:op/inc [2]
                                                                  :op/mult [0.1 0.2]}}})
     6.5))
 )

(defn defmodifier [modifier-k operations]
  (defcomponent modifier-k (data/components operations)))

(defn- stat-k->modifier-k [stat-k]
  (keyword "modifier" (name stat-k)))

(defn defstat [stat-k metam & {:keys [operations]}]
  (defcomponent stat-k metam)
  (defmodifier (stat-k->modifier-k stat-k) operations))

(defstat :stats/hp             data/pos-int-attr :operations [:op/max-inc :op/max-mult])
(defstat :stats/mana           data/nat-int-attr :operations [:op/max-inc :op/max-mult])
(defstat :stats/movement-speed data/pos-attr     :operations [:op/inc :op/mult]) ; TODO only mult required  ( ? )
(defstat :stats/strength       data/nat-int-attr :operations [:op/inc])

(let [doc "action-time divided by this stat when a skill is being used.
          Default value 1.

          For example:
          attack/cast-speed 1.5 => (/ action-time 1.5) => 150% attackspeed."
      skill-speed-stat (assoc data/pos-attr :doc doc)
      operations [:op/inc]]
  (defstat :stats/cast-speed   skill-speed-stat :operations operations)
  (defstat :stats/attack-speed skill-speed-stat :operations operations))

(defstat :stats/armor-save   {:widget :text-field :schema number?} :operations [:op/inc])
(defstat :stats/armor-pierce {:widget :text-field :schema number?} :operations [:op/inc])

; TODO kommt aufs gleiche raus if we have +1 min damage or +1 max damage?
; just inc/mult ?
; or even mana/hp does it make a difference ?
(defmodifier :modifier/damage-deal    [:op/val-inc :op/val-mult :op/max-inc :op/max-mult])
(defmodifier :modifier/damage-receive [:op/inc :op/mult])

(defcomponent :stats/modifiers (data/components [:modifier/damage-deal
                                                 :modifier/damage-receive]))

(extend-type api.entity.Entity
  entity/Stats
  (stat [{:keys [entity/stats]} stat-k]
    (when-let [base-value (stat-k stats)]
      (->effective-value base-value (stat-k->modifier-k stat-k) stats))))

; TODO remove vector shaboink -> gdx.graphics.color/->color use.
(def ^:private hpbar-colors
  {:green     [0 0.8 0]
   :darkgreen [0 0.5 0]
   :yellow    [0.5 0.5 0]
   :red       [0.5 0 0]})

(defn- hpbar-color [ratio]
  (let [ratio (float ratio)
        color (cond
                (> ratio 0.75) :green
                (> ratio 0.5)  :darkgreen
                (> ratio 0.25) :yellow
                :else          :red)]
    (color hpbar-colors)))

(def ^:private borders-px 1)

(comment

 (set! *print-level* nil)

 ; only lady/lord a&b dont have hp/mana/movement-speed ....
 ; => check if it can be optional
 ; => default-values move to stats ...

 (map :property/id
      (filter #(let [{:stats/keys [hp mana movement-speed]} (:entity/stats (:creature/entity %))

                     rslt (not (and hp mana movement-speed))
                     ]
                 (println [hp mana movement-speed])
                 (println rslt)
                 rslt
                 )
              (api.context/all-properties @app.state/current-context :properties/creature)))

 )


; TODO make it all required like in MTG
; then can make effects, e.g. destroy target creature with strength < 3
; mana needed ? idk
; cast-speed/attack-speed also idk (maybe dexterity, intelligence)
; armor-pierce into an effect.... like in wh40k.

; TODO default-values dont make sense
; e.g. in magic creatures have explicitly 0 power
; here they have 0 power but implicitly
; also the code doesnt want to know e.g. armor calculations
; => make it explicit ...
; only attack/cast-speed is quite uninteresting ? could hide ?

(def ^:private stats-keywords
  ; hp/mana/movement-speed given for all entities ?
  ; but maybe would be good to have 'required' key ....
  ; also default values defined with the stat itself ?
  ; armor-pierce move out of here ...
  [:stats/hp ; made optional but bug w. projectile transact damage anyway not checking usable?
   :stats/mana ; made optional @ active-skill
   :stats/movement-speed
   :stats/strength  ; default value 0
   :stats/cast-speed ; has default value 1 @ entity.stateactive-skill
   :stats/attack-speed ; has default value 1 @ entity.stateactive-skill
   :stats/armor-save ; default-value 0
   :stats/armor-pierce ; default-value 0
   ])

; TODO use data/components
; and pass :optional or not ....
; see where else we use data/components ....
; or make it into the component itself if optional or not
; but depends on usage ?
; possible different usage component at different places?
; e.g. audiovisual ?

; or make even hp or movement-speed optional
; for example then cannot attack the princess ... ?

; then the create component here have to move into the component itself.
(defn- build-modifiers [modifiers]
  (into {} (for [[modifier-k operations] modifiers]
             [modifier-k (into {} (for [[operation-k value] operations]
                                    [operation-k [value]]))])))

(comment
 (=
  {:modifier/damage-receive {:op/mult [-0.9]}}
  (build-modifiers {:modifier/damage-receive {:op/mult -0.9}}))
 )

(defcomponent :entity/stats (data/components-attribute :stats)
  (entity/create-component [[_ stats] _components _ctx]
    (-> stats
        (update :stats/hp (fn [hp] (when hp [hp hp])))
        (update :stats/mana (fn [mana] (when mana [mana mana])))
        (update :stats/modifiers build-modifiers)))

  ; TODO proper texts... / widgets / icons ?
  ; HP color based on ratio like hp bar samey (take same color definitions etc.)
  ; mana color same in the whole app
  ; red positive/green negative
  (entity/info-text [[_ {:keys [stats/modifiers] :as stats}] _ctx]
    (str (str/join "\n"
                   (for [stat-k stats-keywords
                         :let [base-value (stat-k stats)]
                         :when base-value]
                     (str (k->pretty-name stat-k) ": " (->effective-value base-value (stat-k->modifier-k stat-k) stats))))
         (let [text-parts (for [[modifier-k operations] modifiers
                                [operation-k values] operations
                                :let [value (reduce + values)]
                                :when (not (zero? value))]
                            (info-text modifier-k [operation-k value]))]
           (when (seq text-parts)
             (str "\n" (str/join "\n" text-parts))))))

  #_(comment
    (let [ctx @app.state/current-context
          entity (api.context/get-entity ctx 55)]
      (-> @entity
          :entity/stats
          :stats/modifiers
          str)))

  (entity/render-info [_
                       {{:keys [width half-width half-height]} :entity/body
                        :keys [entity/mouseover?] :as entity*}
                       g
                       _ctx]
    (when-let [hp (entity/stat entity* :stats/hp)]
      (let [ratio (val-max-ratio hp)
            [x y] (entity/position entity*)]
        (when (or (< ratio 1) mouseover?)
          (let [x (- x half-width)
                y (+ y half-height)
                height (g/pixels->world-units g hpbar-height-px)
                border (g/pixels->world-units g borders-px)]
            (g/draw-filled-rectangle g x y width height color/black)
            (g/draw-filled-rectangle g
                                     (+ x border)
                                     (+ y border)
                                     (- (* width ratio) (* 2 border))
                                     (- height (* 2 border))
                                     (hpbar-color ratio))))))))

(defmethod transact! :tx.entity.stats/pay-mana-cost [[_ entity cost] _ctx]
  (let [mana-val ((entity/stat @entity :stats/mana) 0)]
    (assert (<= cost mana-val))
    [[:tx.entity/assoc-in entity [:entity/stats :stats/mana 0] (- mana-val cost)]]))

(comment
 (let [mana-val 4
       entity (atom (entity/map->Entity {:entity/stats {:stats/mana [mana-val 10]}}))
       mana-cost 3
       resulting-mana (- mana-val mana-cost)]
   (= (transact! [:tx.entity.stats/pay-mana-cost entity mana-cost] nil)
      [[:tx.entity/assoc-in entity [:entity/stats :stats/mana 0] resulting-mana]]))
 )

(defmacro def-set-to-max-effect [stat]
  `(let [component# ~(keyword "effect" (str (name (namespace stat)) "-" (name stat) "-set-to-max"))]
     (defcomponent component# {:widget :label
                               :schema [:= true]
                               :default-value true}
       (effect/text ~'[_ _effect-ctx]
         ~(str "Sets " (name stat) " to max."))

       (effect/usable? ~'[_ _effect-ctx] true)

       (effect/useful? ~'[_ {:keys [effect/source]} _ctx]
         (lower-than-max? (~stat (:entity/stats @~'source))))

       (transact! ~'[_ {:keys [effect/source]}]
         [[:tx.entity/assoc-in ~'source [:entity/stats ~stat] (set-to-max (~stat (:entity/stats @~'source)))]]))))

(def-set-to-max-effect :stats/hp)
(def-set-to-max-effect :stats/mana)

#_(defcomponent :effect/set-to-max {:widget :label
                                    :schema [:= true]
                                    :default-value true}
  (effect/text [[_ stat] _effect-ctx]
    (str "Sets " (name stat) " to max."))

  (effect/usable? [_ {:keys [effect/source]}]
    source)

  (effect/useful? [[_ stat] {:keys [effect/source]} _ctx]
    (lower-than-max? (stat @source)))

  (effect/txs [[_ stat] {:keys [effect/source]}]
    [[:tx.entity/assoc source stat (set-to-max (stat @source))]]))

; this as macro ... ? component which sets the value of another component ??
#_(defcomponent :effect/set-mana-to-max {:widget :label
                                         :schema [:= true]
                                         :default-value true}
  (effect/usable? [_ {:keys [effect/source]}] source)
  (effect/text    [_ _effect-ctx]     (effect/text    [:effect/set-to-max :entity/mana]))
  (effect/useful? [_ effect-ctx _ctx] (effect/useful? [:effect/set-to-max :entity/mana] effect-ctx))
  (effect/txs     [_ effect-ctx]      (effect/txs     [:effect/set-to-max :entity/mana] effect-ctx)))

#_[:effect/set-to-max :entity/hp]
#_[:effect/set-to-max :entity/mana]

(defn- entity*->melee-damage [entity*]
  (let [strength (or (entity/stat entity* :stats/strength) 0)]
    {:damage/min-max [strength strength]}))

(defn- damage-effect [{:keys [effect/source]}]
  [:effect/damage (entity*->melee-damage @source)])

(defcomponent :effect/melee-damage {}
  (effect/text [_ {:keys [effect/source] :as effect-ctx}]
    (str "Damage based on entity strength."
         (when source
           (str "\n" (effect/text (damage-effect effect-ctx)
                                  effect-ctx)))))

  (effect/usable? [_ effect-ctx]
    (effect/usable? (damage-effect effect-ctx) effect-ctx))

  (transact! [_ ctx]
    [(damage-effect ctx)]))

(defn- effective-armor-save [source* target*]
  (max (- (or (entity/stat target* :stats/armor-save) 0)
          (or (entity/stat source* :stats/armor-pierce) 0))
       0))

(comment
 ; broken
 (let [source* {:entity/stats {:stats/armor-pierce 0.4}}
       target* {:entity/stats {:stats/armor-save   0.5}}]
   (effective-armor-save source* target*))
 )

(defn- armor-saves? [source* target*]
  (< (rand) (effective-armor-save source* target*)))

(defn- ->effective-damage [damage source*]
  (update damage :damage/min-max ->effective-value :modifier/damage-deal (:entity/stats source*)))

(comment
 (let [->stats (fn [mods] {:entity/stats {:stats/modifiers mods}})]
   (and
    (= (->effective-damage {:damage/min-max [5 10]}
                           (->stats {:modifier/damage-deal {:op/val-inc [1 5 10]
                                                            :op/val-mult [0.2 0.3]
                                                            :op/max-mult [1]}}))
       #:damage{:min-max [31 62]})

    (= (->effective-damage {:damage/min-max [5 10]}
                           (->stats {:modifier/damage-deal {:op/val-inc [1]}}))
       #:damage{:min-max [6 10]})

    (= (->effective-damage {:damage/min-max [5 10]}
                           (->stats {:modifier/damage-deal {:op/max-mult [2]}}))
       #:damage{:min-max [5 30]})

    (= (->effective-damage {:damage/min-max [5 10]}
                           (->stats nil))
       #:damage{:min-max [5 10]}))))

(defn- damage->text [{[min-dmg max-dmg] :damage/min-max}]
  (str min-dmg "-" max-dmg " damage"))

(defcomponent :damage/min-max data/val-max-attr)

(defcomponent :effect/damage (data/map-attribute :damage/min-max)
  (effect/text [[_ damage] {:keys [effect/source]}]
    (if source
      (let [modified (->effective-damage damage @source)]
        (if (= damage modified)
          (damage->text damage)
          (str (damage->text damage) "\nModified: " (damage->text modified))))
      (damage->text damage))) ; property menu no source,modifiers

  (effect/usable? [_ {:keys [effect/target]}]
    (and target
         ; TODO check for creature stats itself ? or just hp ?
         (entity/stat @target :stats/hp)))

  (transact! [[_ damage] {:keys [effect/source effect/target]}]
    (let [source* @source
          target* @target
          hp (entity/stat target* :stats/hp)]
      (cond
       (zero? (hp 0))
       []

       (armor-saves? source* target*)
       [[:tx/add-text-effect target "[WHITE]ARMOR"]] ; TODO !_!_!_!_!_!

       :else
       (let [{:keys [damage/min-max]} (->effective-damage damage source*)
             ;_ (println "\nmin-max:" min-max)
             dmg-amount (random/rand-int-between min-max)
             ;_ (println "dmg-amount: " dmg-amount)
             dmg-amount (->pos-int (->effective-value dmg-amount :modifier/damage-receive (:entity/stats target*)))
             ;_ (println "effective dmg-amount: " dmg-amount)
             new-hp-val (max (- (hp 0) dmg-amount) 0)]
         [[:tx.entity/audiovisual (entity/position target*) :audiovisuals/damage]
          [:tx/add-text-effect target (str "[RED]" dmg-amount)]
          [:tx.entity/assoc-in target [:entity/stats :stats/hp 0] new-hp-val]
          [:tx/event target (if (zero? new-hp-val) :kill :alert)]])))))
