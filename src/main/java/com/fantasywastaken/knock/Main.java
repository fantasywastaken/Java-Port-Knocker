package com.fantasywastaken.knock;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Options opts;
        try {
            opts = Options.parse(args);
        } catch (ArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage(System.err);
            System.exit(2);
            return;
        }

        if (opts.showHelp) {
            printUsage(System.out);
            return;
        }

        InetAddress addr;
        try {
            addr = InetAddress.getByName(opts.host);
        } catch (UnknownHostException e) {
            System.err.println("Cannot resolve host: " + opts.host);
            System.exit(3);
            return;
        }

        System.out.printf("Knocking %s (%s) on %d port(s), delay=%dms, timeout=%dms%n",
                opts.host, addr.getHostAddress(), opts.ports.size(), opts.delayMs, opts.timeoutMs);

        int success = 0;
        for (int i = 0; i < opts.ports.size(); i++) {
            int port = opts.ports.get(i);
            long startNs = System.nanoTime();
            KnockResult result = knock(addr, port, opts.timeoutMs);
            double elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0;

            String status = switch (result.state) {
                case OPEN -> "OPEN";
                case REFUSED -> "REFUSED";
                case TIMEOUT -> "TIMEOUT";
                case ERROR -> "ERROR";
            };
            if (opts.verbose && result.detail != null) {
                System.out.printf("  [%d/%d] %d -> %s (%.1f ms) %s%n",
                        i + 1, opts.ports.size(), port, status, elapsedMs, result.detail);
            } else {
                System.out.printf("  [%d/%d] %d -> %s (%.1f ms)%n",
                        i + 1, opts.ports.size(), port, status, elapsedMs);
            }
            if (result.state != KnockState.ERROR) success++;

            if (i + 1 < opts.ports.size() && opts.delayMs > 0) {
                try {
                    Thread.sleep(opts.delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Interrupted.");
                    System.exit(130);
                    return;
                }
            }
        }

        System.out.printf("Done: %d/%d knocks delivered%n", success, opts.ports.size());
    }

    private static KnockResult knock(InetAddress addr, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(addr, port), timeoutMs);
            return new KnockResult(KnockState.OPEN, null);
        } catch (java.net.SocketTimeoutException e) {
            return new KnockResult(KnockState.TIMEOUT, null);
        } catch (java.net.ConnectException e) {
            return new KnockResult(KnockState.REFUSED, e.getMessage());
        } catch (IOException e) {
            return new KnockResult(KnockState.ERROR, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void printUsage(java.io.PrintStream out) {
        out.println("Usage: java -jar knock.jar <host> <port> [<port> ...] [options]");
        out.println();
        out.println("Options:");
        out.println("  --delay <ms>     Delay between knocks in milliseconds (default 200)");
        out.println("  --timeout <ms>   Per-knock connect timeout in milliseconds (default 500)");
        out.println("  --verbose        Print underlying error text for failed knocks");
        out.println("  -h, --help       Show this help");
        out.println();
        out.println("Example:");
        out.println("  java -jar knock.jar host.example 1234 5678 9012 --delay 200");
    }

    private enum KnockState { OPEN, REFUSED, TIMEOUT, ERROR }

    private record KnockResult(KnockState state, String detail) {}

    private static final class Options {
        String host;
        List<Integer> ports = new ArrayList<>();
        int delayMs = 200;
        int timeoutMs = 500;
        boolean verbose = false;
        boolean showHelp = false;

        static Options parse(String[] args) {
            Options o = new Options();
            int i = 0;
            while (i < args.length) {
                String a = args[i];
                switch (a) {
                    case "-h", "--help" -> {
                        o.showHelp = true;
                        return o;
                    }
                    case "--delay" -> {
                        if (i + 1 >= args.length) throw new ArgumentException("--delay requires a value");
                        o.delayMs = parseNonNegative(args[++i], "--delay");
                    }
                    case "--timeout" -> {
                        if (i + 1 >= args.length) throw new ArgumentException("--timeout requires a value");
                        o.timeoutMs = parsePositive(args[++i], "--timeout");
                    }
                    case "--verbose" -> o.verbose = true;
                    default -> {
                        if (a.startsWith("-")) throw new ArgumentException("Unknown option: " + a);
                        if (o.host == null) {
                            o.host = a;
                        } else {
                            o.ports.add(parsePort(a));
                        }
                    }
                }
                i++;
            }
            if (o.host == null) throw new ArgumentException("Host is required");
            if (o.ports.isEmpty()) throw new ArgumentException("At least one port is required");
            return o;
        }

        private static int parsePort(String s) {
            int p;
            try {
                p = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                throw new ArgumentException("Invalid port: " + s);
            }
            if (p < 1 || p > 65535) throw new ArgumentException("Port out of range: " + s);
            return p;
        }

        private static int parseNonNegative(String s, String flag) {
            int v;
            try {
                v = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                throw new ArgumentException(flag + " expects an integer, got: " + s);
            }
            if (v < 0) throw new ArgumentException(flag + " must be >= 0");
            return v;
        }

        private static int parsePositive(String s, String flag) {
            int v = parseNonNegative(s, flag);
            if (v == 0) throw new ArgumentException(flag + " must be > 0");
            return v;
        }
    }

    private static final class ArgumentException extends RuntimeException {
        ArgumentException(String message) {
            super(message);
        }
    }
}
