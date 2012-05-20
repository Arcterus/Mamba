all: mamba

OUTPUT = mamba/server.scala

mamba:
	scalac mamba/server.scala
	jar cfm ../bin/mamba.jar ../mamba.manifest -C mamba/ .

.PHONY: clean
	rm -r *.class
