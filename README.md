# Java-Port-Knocker

A dependency-free CLI TCP port knocker written against `java.net.Socket`, delivering a scripted sequence of connection attempts against a host.

### How It Works

Port knocking asks a firewall to open a real service (SSH, VPN, admin panel) only after seeing a specific ordered sequence of connection attempts against otherwise-closed ports. This client opens a fresh `java.net.Socket` for each port in the sequence and calls `connect(new InetSocketAddress(host, port), timeoutMs)`, which sends a real TCP SYN and blocks up to the configured timeout. The connection outcome is classified: `OPEN` when the peer accepts, `REFUSED` on `ConnectException`, `TIMEOUT` on `SocketTimeoutException`, or `ERROR` for any other I/O failure. Between knocks the tool sleeps for `--delay` milliseconds so servers running `knockd` or `pf` have time to advance their state machine. Every attempt is printed as `[i/N] port -> STATUS (elapsed ms)`, and a summary line closes the run.

## Setup

### Requirements

- Java 17 or newer
- Apache Maven 3.8+

### Build

```bash
git clone <this-repo>
cd Java-Port-Knocker
mvn -q clean package
```

The runnable jar lands at `target/knock.jar`.

### Usage

```bash
java -jar target/knock.jar host.example 1234 5678 9012 --delay 200
java -jar target/knock.jar 10.0.0.1 7000 8000 9000 --timeout 300 --verbose
```

Flags:

| Flag             | Default | Purpose                                        |
| ---------------- | ------- | ---------------------------------------------- |
| `--delay <ms>`   | `200`   | Delay between knocks in milliseconds           |
| `--timeout <ms>` | `500`   | Per-knock connect timeout in milliseconds      |
| `--verbose`      | off     | Print underlying error text on failed knocks   |
| `-h`, `--help`   |         | Show usage                                     |

Positional arguments: `<host>` first, then one or more `<port>` values in the exact sequence you want to send.

### Features

- Only depends on `java.net.Socket` from the JDK; no third-party libraries
- Real TCP SYN attempts, not raw sockets, so it works without root/admin
- Configurable per-knock timeout and inter-knock delay
- Clear per-attempt output with elapsed milliseconds
- Distinguishes OPEN vs REFUSED vs TIMEOUT vs ERROR
- Verbose mode surfaces the underlying `IOException` message
- Interrupt-safe: Ctrl-C stops between knocks with exit code 130
- Packaged as a single runnable jar via `maven-jar-plugin`
