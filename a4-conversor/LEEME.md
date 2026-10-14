# a4-conversor · actividad 4 de AD-UD1

El conversor de pedidos XML → CSV y JSON que escribió una IA. Funciona con el fichero de prueba y se rompe con
casi todo lo demás. No se escribe nada desde cero: se **lee**, se **demuestra** y se **toca**.

    src/main/java/ad/a4/original/   el código de la IA, sin tocar. Es lo que se lee y se audita
    src/main/java/ad/a4/tocar/      una copia del mismo código, para los dos cambios de la actividad
    src/test/java/ad/a4/            OriginalTest (siete pruebas, que FALLAN a propósito) y Actividad4Test (los dos cambios)

    mvn test -Dtest=OriginalTest      # tiene que salir rojo: cada prueba roja es un fallo del código de la IA
    mvn test -Dtest=Actividad4Test    # tras quitar @Disabled y hacer los dos cambios, verde
