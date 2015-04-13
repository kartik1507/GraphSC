find ../ -name "*.java" > source.txt;
#javac -cp .:../lib/FlexSC.jar:../lib/commons-cli-1.2.jar:../lib/commons-io-2.4.jar -d . @source.txt;
javac -cp .:../lib/* -d . @source.txt;
rm source.txt;
