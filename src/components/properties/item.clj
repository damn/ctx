(ns components.properties.item
  (:require [clojure.string :as str]
            [core.component :as component :refer [defcomponent]]
            [core.data :as data]
            [core.effect :as effect]
            [core.modifiers :as modifiers]))

(com.badlogic.gdx.graphics.Colors/put "ITEM_GOLD"
                                      (com.badlogic.gdx.graphics.Color. (float 0.84)
                                                                        (float 0.8)
                                                                        (float 0.52)
                                                                        (float 1)))

(defcomponent :properties/item
  (component/create [_ _ctx]

    ; could make optional - shorter properties.edn ...
    ; and some items could just be fluff
    (defcomponent :item/modifiers {:schema [:components-ns :modifier]})

    (defcomponent :item/slot {:schema [:qualified-keyword {:namespace :inventory.slot}]}) ; TODO one of ... == 'enum' !!

    {:id-namespace "items"
     :schema (data/map-attribute-schema
              [:property/id [:qualified-keyword {:namespace :items}]]
              [:property/pretty-name
               :property/image
               :item/slot
               :item/modifiers])
     :edn-file-sort-order 3
     :overview {:title "Items"
                :columns 17
                :image/dimensions [60 60]
                :sort-by-fn #(vector (if-let [slot (:item/slot %)]
                                       (name slot)
                                       "")
                                     (name (:property/id %)))}
     :->text (fn [ctx
                  {:keys [property/pretty-name
                          item/modifiers]
                   :as item}]
               [(str "[ITEM_GOLD]" pretty-name (when-let [cnt (:count item)] (str " (" cnt ")")) "[]")
                (when (seq modifiers)
                  (modifiers/info-text modifiers))])}))

; TODO use image w. shadows spritesheet
(defcomponent :tx.entity/item
  (effect/do! [[_ position item] _ctx]
    (assert (:property/image item))
    [[:tx/create
      {:position position
       :width 0.5 ; TODO use item-body-dimensions
       :height 0.5
       :z-order :z-order/on-ground}
      #:entity {:image (:property/image item)
                :item item
                :clickable {:type :clickable/item
                            :text (:property/pretty-name item)}}]]))
