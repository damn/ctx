(ns app.start
  (:require [gdx :as gdx]
            [gdx.backends.lwjgl3 :as lwjgl3]
            [gdx.graphics.color :as color]
            [gdx.utils.screen-utils :as screen-utils]
            [core.component :as component]
            [api.context :as ctx]
            [api.screen :as screen]
            [context.screens :as screens]
            [context.graphics.views :as views]
            [app.state :refer [current-context]]))

(defn- ->context [context]
  (component/load! context)
  (component/build (ctx/->Context) component/create context :log? false))

(defn- create-context [context]
  (->> context
       ->context
       screens/init-first-screen
       (reset! current-context)))

(defn- destroy-context []
  (component/run-system! component/destroy @current-context))

(defn- render-context []
  (views/fix-viewport-update @current-context)
  (screen-utils/clear color/black)
  (-> @current-context
      ctx/current-screen
      screen/render!))

(defn- update-viewports [w h]
  (views/update-viewports @current-context w h))

(defn- ->application [context]
  (gdx/->application-listener
   :create #(create-context context)
   :dispose destroy-context
   :render render-context
   :resize update-viewports))

(defn start [config]
  (assert (:context config))
  (lwjgl3/->application (->application (:context config))
                        (lwjgl3/->configuration (:app config))))
