(ns backend.core
  (:gen-class)
  (:require [mount.core :refer [args defstate] :as mount]
            [cprop.core :refer [load-config]]
            [cprop.source :as source]
            [clojure.tools.logging :as log]
            [org.httpkit.server :as http]
            [nrepl.server :as nrepl]
            [backend.config :refer [env]]
            [backend.env :refer [defaults]]
            [backend.handler :refer [app]]))



(mount/defstate init-app
  :start ((or (:init defaults) identity))
  :stop  ((or (:stop defaults) identity)))


(mount/defstate ^{:on-reload :noop} repl-server
  :start
  (when-let [nrepl-port (env :nrepl-port)]
    (log/info "starting nREPL server on port" nrepl-port)
    (nrepl/start-server :port nrepl-port))
  :stop
  (when repl-server
    (nrepl/stop-server repl-server)
    (log/info "nREPL server stopped")))


(mount/defstate ^{:on-reload :noop} http-server
  :start
  (when-let [port (env :port)]
    (try
      (log/info "starting HTTP server on port" port)
      (http/run-server app {:port port
                            :thread (:thread env 4)
                            :max-body (:max-body env)})
      (catch Throwable t
        (log/error t (str "server failed to start on port " port))
        (throw t))))
  :stop
  (when-not (nil? http-server)
    (http-server :timeout 100)
    (log/info "HTTP server stopped")))


(defn stop-app []
  (doseq [component (:stopped (mount/start))]
    (log/info component "stopped"))
  (shutdown-agents))


(defn start-app []
  (doseq [component (:started (mount/start))]
    (log/info component "started"))
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))


(defn -main[& args]
  (start-app))
