(ns org.pipo.ui-utils
  (:require
    [neko.threading :refer [on-ui]]
    [neko.ui :refer [config]]
    [neko.find-view :refer [find-view]]
    ))

(defn set-text [ctx elmt s]
  (on-ui (config (find-view ctx elmt) :text s)))

(defn get-text [ctx elmt]
  (str (.getText ^android.widget.TextView (find-view ctx elmt))))
