all: mamba

OUTPUT = mamba/server.class

mamba:
	scalac mamba/server.scala
	jar cfm ../bin/mamba.jar ../mamba.manifest $(OUTPUT) .

.PHONY: mamba clean

clean:
	rm -r mamba/*.class
