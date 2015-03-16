find ../ -name "*.java" > source.txt;
find ../../my_FlexSC/ -name "*.java" >> source.txt;
javac -cp .:../../my_FlexSC/bin:../../my_FlexSC/lib/* -d . @source.txt;
rm source.txt;
