all: mamba

mamba:
	scalac -o bin/mamba src/server.scala

.PHONY: clean install

clean:
	rm -r /etc/mamba.conf /usr/local/bin/mamba

install:
	install conf/* /etc
	install bin/mamba /usr/local/bin/mamba
