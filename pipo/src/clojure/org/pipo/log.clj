(ns org.pipo.log
  (:require [neko.log :as log]))

(def ^:const TAG "pipo")

(defmacro e [& args]
  `(log/e ~@args :tag TAG))

(defmacro w [& args]
  `(log/w ~@args :tag TAG))

(defmacro i [& args]
  `(log/i ~@args :tag TAG))

(defmacro d [& args]
  `(log/d ~@args :tag TAG))

(defmacro v [& args]
  `(log/v ~@args :tag TAG))
