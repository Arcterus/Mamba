all: mamba

mamba:
	mkdir -p bin
	cd src && make

.PHONY: mamba uninstall install clean

uninstall:
	rm -r /etc/mamba.conf /usr/local/bin/mamba

install:
	install conf/* /etc
	install bin/mamba /usr/local/bin/mamba

clean:
	cd src && make clean
	rm -r bin
