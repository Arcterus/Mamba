all: mamba

mamba:
	scalac mamba/server.scala
	jar cfm ../bin/mamba.jar ../mamba.manifest mamba/*.class  -C ../lib/ .

.PHONY: mamba clean

clean:
	rm -r mamba/*.class
