(ns entity-state.player-dead
  (:require [core.context :as ctx]
            [core.entity-state :as state]))

(defrecord PlayerDead [eid]
  state/PlayerState
  (player-enter [_] [[:tx.context.cursor/set :cursors/black-x]])
  (pause-game? [_] true)
  (manual-tick [_ context])
  (clicked-inventory-cell [_ cell])
  (clicked-skillmenu-skill [_ skill])

  state/State
  (enter [_ _ctx]
    [[:tx/sound "sounds/bfxr_playerdeath.wav"]
     [:tx/player-modal {:title "YOU DIED"
                        :text "\nGood luck next time"
                        :button-text ":("
                        :on-click #(ctx/change-screen % :screens/main-menu)}]])
  (exit [_ _ctx])
  (tick [_ _ctx])

  (render-below [_ entity* g ctx])
  (render-above [_ entity* g ctx])
  (render-info  [_ entity* g ctx]))

(defn ->build [ctx eid _params]
  (->PlayerDead eid))
