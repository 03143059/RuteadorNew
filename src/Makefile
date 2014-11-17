make: ForwarderMessage.class ForwardingService.class Ruteador.class RuteadorWindow.class dv\Broadcaster.class dv\NbrCostPair.class dv\Neighbor.class dv\Router.class

ForwarderMessage.class: ForwarderMessage.java
    javac $?

ForwardingService.class: ForwardingService.java
    javac $?

Ruteador.class: Ruteador.java
    javac $?

RuteadorWindow.class: RuteadorWindow.java
    javac $?

dv\Broadcaster.class: dv\Broadcaster.java
    javac $?

dv\NbrCostPair.class: dv\NbrCostPair.java
    javac $?

dv\Neighbor.class: dv\Neighbor.java
    javac $?

dv\Router.class: dv\Router.java
    javac $?

test:
    start.bat
    java Ruteador -i 11 rutas2.txt

clean:
    @del /q /s *.class  2>nul


