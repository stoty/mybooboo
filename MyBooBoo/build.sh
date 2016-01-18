#!/bin/sh
cd src 
javac  -d ../bin -cp ../lib/*.jar hu/stoty/mybooboo/MyBooBoo.java hu/stoty/mybooboo/FieldToString.java
cd -