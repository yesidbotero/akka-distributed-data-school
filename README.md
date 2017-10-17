# Suits de tests de Akka Distributed Data
En este proyecto de prueban las propiedades de los diferentes CRDTs y sus casos de uso.
Debido a que Akka Distributed Data es una herramienta útil para compartir datos entre nodos en un Cluster de Akka, es objetivo que los test sean ejecutados en diferentes nodos.
# Tests MultiJvm
Para ejecutar tests multi-jvm es necesario tener previamente instalado el plugin de sbt,
para instalarlo basta con agregar la siguiente línea al archivo /project/plugins.sbt:
addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.4.0")

Adicionalmente agregar la librería TestKit para tests multi-nodo al proyecto sbt:
"com.typesafe.akka" %% "akka-multi-node-testkit" % "2.5.6"

# Ejecutar los tests
 - Para ejecutar todos los test multi-jvm presentes en el proyecto, ejecutar en la consola de ```sbt: multi-jvm:test```
 - Para ejecutar una suit de tests multi-jvm específica, ejecutar en la consola de sbt: ```multi-jvm:test-only NombreDeTest```

> **Nota:**
> - Los nombres de las suits deben seguir la siguiente convención:
```{NombreDeSuit}MultiJvm{NombreDelNodo}```
por ejemplo:
```GreeterSpecMultiJvmNode1```
> - Todos los test multi-jvm están ubicados por convención en:
```/src/multi-jvm```
> - Los tests asociados al mismo NombreDeSuit van a ser ejecutados juntos
> - NombreDelNodo en el nombre con el cual se identificará cada JVM.
