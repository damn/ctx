(ns entity.image
  (:require [core.component :as component]
            [api.graphics :as g]
            [api.entity :as entity]))

(component/def :entity/image {}
  image
  (entity/render-default
    [_ {:keys [entity/position entity/body]} g _ctx]
    (g/draw-rotated-centered-image g
                                   image
                                   (if body (:rotation-angle body) 0)
                                   position)))
