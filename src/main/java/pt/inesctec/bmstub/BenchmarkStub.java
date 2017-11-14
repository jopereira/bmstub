package pt.inesctec.bmstub;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class BenchmarkStub {
    private static Logger logger = LoggerFactory.getLogger(BenchmarkStub.class);

    private final int maxThreads = 5;
    private final int avgTime = 100;

    private final HttpServer server;
    private long count = 0;

    private RandomGenerator random = new MersenneTwister();

    public BenchmarkStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.setExecutor(Executors.newFixedThreadPool(maxThreads));

        HttpHandler[] handlers = new HttpHandler[] {
            new Constant(),
            new Stable(),
            new StableVariable(),
            new Unstable(),
            new WarmUp(),
            new LongTail(),
            new Bimodal()
        };

        server.createContext("/", new Top(handlers));
        for(int i=0; i<handlers.length; i++)
            server.createContext("/"+handlers[i].getClass().getSimpleName().toLowerCase(), handlers[i]);

    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public static void main(String[] args) throws Exception {
        BenchmarkStub m = new BenchmarkStub();
        m.start();
        logger.info("http server started at: http://localhost:8000/");
        System.in.read();
        m.stop();
        logger.info("http server stoped.");
    }

    private synchronized long getRequest() {
        return count++;
    }

    private class Top implements HttpHandler {
        private final String text;

        public Top(HttpHandler[] handlers) {
            StringBuffer buf = new StringBuffer();

            buf.append("<html><head><title>Benchmark Stub</title></head><body>");
            buf.append("<h1>Benchmark Stub</h1><h2>Request types</h2><ul>");
            for(int i=0; i<handlers.length; i++) {
                String name = handlers[i].getClass().getSimpleName();
                buf.append(String.format("<li><a href=\"%s\">%s</a></li>", name.toLowerCase(), name));
            }
            buf.append("</ul><h2>More info...</h2>See source code and documentation in <a href=\"https://github.com/jopereira/bmstub\">github</a>.");
            buf.append("</body></html>");
            text = buf.toString();
        }

        public void handle(HttpExchange request) throws IOException {
            request.sendResponseHeaders(200, text.length());
            OutputStream os = request.getResponseBody();
            os.write(text.getBytes());
            os.close();
        }
    }

    private abstract class BasetHandler implements HttpHandler {
        public void handle(HttpExchange request) throws IOException {
            long req = getRequest();

            try {
                work(req);
            } catch (InterruptedException e) {
                // don't care
            }

            String response = String.format("<html><head><title>Benchmark Stub</title></head><body>Request %d of type %s executed.<p><a href=\"/\">Back.</a></body></html>", count, this.getClass().getSimpleName());
            request.sendResponseHeaders(200, response.length());
            OutputStream os = request.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        protected abstract void work(long req) throws InterruptedException;
    }

    private class Constant extends BasetHandler {
        protected void work(long req) throws InterruptedException {
            Thread.sleep(avgTime);
        }
    }

    private class Stable extends BasetHandler {
        private TruncNormalDistribution dist = new TruncNormalDistribution(random, avgTime, avgTime/4);

        protected void work(long req) throws InterruptedException {
            Thread.sleep((long)dist.sample());
        }
    }

    private class StableVariable extends BasetHandler {
        private TruncNormalDistribution dist = new TruncNormalDistribution(random, avgTime, avgTime/2);

        protected void work(long req) throws InterruptedException {
            Thread.sleep((long)dist.sample());
        }
    }

    private class WarmUp extends Stable {

        private static final double stability = 500;

        protected void work(long req) throws InterruptedException {
            super.work(req);

            if (req < stability) {
                int spread = (int) (((stability-req)/stability)*avgTime*2);
                int time = random.nextInt(spread);
                Thread.sleep(time);
            }
        }
    }

    private class Unstable extends Stable {

        private static final double stability = 10;

        protected void work(long req) throws InterruptedException {
            super.work(req);
            int mean = (int) (req/stability);
            int time = random.nextInt(mean);
            Thread.sleep(time);
        }
    }

    private class LongTail extends Stable {

        private static final int max = 2000;
        private static final double prob = 0.002;

        private UniformIntegerDistribution size = new UniformIntegerDistribution(random, max/2, max*3/2);
        private UniformRealDistribution when = new UniformRealDistribution(random, 0, 1);

        protected void work(long req) throws InterruptedException {
            super.work(req);
            if (when.sample() <= prob)
                Thread.sleep(size.sample());
        }
    }

    private class Bimodal extends BasetHandler {
        private static final int shift = 50;

        private RealDistribution[] dists = {
                new TruncNormalDistribution(random, avgTime+shift, avgTime/5),
                new TruncNormalDistribution(random, avgTime-shift, avgTime/5)
        };
        private UniformIntegerDistribution dist = new UniformIntegerDistribution(random, 0, dists.length-1);

        protected void work(long req) throws InterruptedException {
            Thread.sleep((long) dists[dist.sample()].sample());
        }
    }
}
