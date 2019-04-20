#Author: Prashanth Mallyampatti
#!/usr/bin/bash

rm *.class

javac *.java

rm servertest.txt

touch servertest.txt

kill -9 $(lsof -ti:7735) 
