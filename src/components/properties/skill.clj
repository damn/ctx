(ns components.properties.skill
  (:require [clojure.string :as str]
            [utils.core :refer [readable-number]]
            [core.component :refer [defcomponent] :as component]
            [core.components :as components]
            [core.context :as ctx]))

(defcomponent :skill/action-time {:data :pos}
  (component/info-text [[_ v] _ctx]
    (str "[GOLD]Action-Time: " (readable-number v) " seconds[]")))

(defcomponent :skill/cooldown {:data :nat-int}
  (component/info-text [[_ v] _ctx]
    (when-not (zero? v)
      (str "[SKY]Cooldown: " (readable-number v) " seconds[]"))))

(defcomponent :skill/cost {:data :nat-int}
  (component/info-text [[_ v] _ctx]
    (when-not (zero? v)
      (str "[CYAN]Cost: " v " Mana[]"))))

(defcomponent :skill/effects {:data [:components-ns :effect]}
  #_(component/info-text [[_ v] ctx]
    ; don't used player-entity* as it may be nil when just created, could use the current property creature @ editor
    (str "[CHARTREUSE]"
         (components/info-text v (assoc ctx :effect/source (ctx/player-entity ctx)))
         "[]")))

(defcomponent :skill/start-action-sound {:data :sound})

(defcomponent :skill/action-time-modifier-key {:data [:enum [:stats/cast-speed :stats/attack-speed]]}
  (component/info-text [[_ v] _ctx]
    (str "[VIOLET]" (case v
                      :stats/cast-speed "Spell"
                      :stats/attack-speed "Attack") "[]")))

; effect-ctx safe-merge into effect value !
; can let
; no merge ctx
; ?

; * sounds move into action .... grep tx/sound anyway remv
; => but what abt. target-entity? (in case of hit ground there's something already?)
; should be the 'abort' case??? and not
; & damage already has its own sound/audiovisual ?
; => each group of 'effects' has its sound ? e.g. damage grouped with 3 other things, make its own sound

; * get clear creature components/stats which required (hp for princess ... because potential field etc.)
; and flatten _


; can attack own faction w. melee ..

; (component/schema :skill/effects)
; TODO how on restarts clear out core.component/attributes & defsystems  & reload _ALL_ affecet ns's defcomponents ??

; => its part of context then ?

; al data needs also to supply default value
; e.g. DRY
; convert nil cannot set it

; Bow shouldn't have player modified stuff...

; Window doesn't icnrease size on change (add components)
; anyway full size?
; also use tree? its messy ....
; => needs to be nice...

; maxrange needs to be connected w. los - max player range / creatures
; etc.
; and optional

; TODO effect/projectile
; needs direction & source & targeT?!
; its what effect-target type usable?
; player usable in just direction, NPC's use it w. target-entity to check if possible ....

; or just keep  effect - projectile fits there
; but other ones cannot use directly -
; - entity-effect -  ?

; TODO tx.ui / tx.create / tx.entity
; or with effect
; but tx. shorter

; but doesn't work with effect/hp => stats/hp
; or always effect.entity/_stat_
; autocreate

; :entity.effect/faction
; :effect.entity/faction ?
;
; :entity-effect/damage
; :hit-effect/damage
; :tx.entity/damage
; :tx.e/damage
; ?

; * convert
; * ::stat-effect
; * melee-damage
; * damage
; * stun/kill (use all events, add even 'interrupt' or destroy' ?

; => SO ITS ABOUT definint hit-effects (also use @ projectile hit-effects)
; or entity-effects???
; there are more ... also add-skill/etc.?
; see defcomponents do! ...


; TODO effect-target/position => {:effect/spawn :creatures/skeleton-warrior}
; don't pass anymore effect/source & effect/target etc.
; =. effect-target/position or direction?? {:effect/projectile :projectiles/black}
; (more complicated ...)

; TODO probably action/sound and not sound at low lvl places ... ?

; TODO can I move other skill related stuff over here? grep key usage ???

(defcomponent :properties/skill
  (component/create [_ _ctx]
    {:id-namespace "skills"
     :schema [[:property/id [:qualified-keyword {:namespace :skills}]]
              [:property/pretty-name
               :property/image
               :skill/action-time
               :skill/cooldown
               :skill/cost
               :skill/effects
               :skill/start-action-sound
               :skill/action-time-modifier-key]]
     :edn-file-sort-order 0
     :overview {:title "Skills"
                :columns 16
                :image/dimensions [70 70]}}))
