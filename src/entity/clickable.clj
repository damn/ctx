(ns entity.clickable
  (:require [core.component :refer [defcomponent]]
            [core.graphics :as g]
            [core.entity :as entity]))

(defcomponent :entity/clickable {}
  (entity/render-default [[_ {:keys [text]}]
                          {:keys [entity/mouseover?] :as entity*}
                          g
                          _ctx]
    (when (and mouseover? text)
      (let [[x y] (:position entity*)]
        (g/draw-text g
                     {:text text
                      :x x
                      :y (+ y (:half-height entity*))
                      :up? true})))))
