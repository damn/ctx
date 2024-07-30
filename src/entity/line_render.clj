(ns entity.line-render
  (:require [core.component :as component]
            [api.graphics :as g]
            [api.entity :as entity]))

(component/def :entity/line-render {}
  {:keys [thick? end color]}
  (entity/render-default [_ {:keys [entity/position]} g _ctx]
    (if thick?
      (g/with-shape-line-width g 4
        #(g/draw-line g position end color))
      (g/draw-line g position end color))))
