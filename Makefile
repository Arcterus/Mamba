all: mamba

OUTPUT = mamba/server.class

mamba:
	scalac mamba/server.scala
	jar cfm mamba.jar mamba.manifest -C mamba/ .

.PHONY: mamba uninstall install clean

uninstall:
	rm -r /etc/mamba.conf /usr/local/bin/mamba

install:
	install conf/* /etc
	install bin/mamba /usr/local/bin/mamba

clean:
	cd src && make clean
