package de.unihamburg.informatik.nosqlmark.db;

import com.yahoo.ycsb.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

import java.util.*;

/**
 * YCSB binding for <a href="http://redis.io/">Redis</a> using JedisPool.
 *
 * See {@code redis/README.md} for details.
 */
public class RedisJedisPoolClient extends DB {

    private JedisPool pool;

    public static final String HOST_PROPERTY = "redis.host";
    public static final String PORT_PROPERTY = "redis.port";
    public static final String PASSWORD_PROPERTY = "redis.password";

    public static final String INDEX_KEY = "_indices";

    public void init() throws DBException {
        Properties props = getProperties();
        int port;

        String portString = props.getProperty(PORT_PROPERTY);
        if (portString != null) {
            port = Integer.parseInt(portString);
        } else {
            port = Protocol.DEFAULT_PORT;
        }
        String host = props.getProperty(HOST_PROPERTY);

        String password = props.getProperty(PASSWORD_PROPERTY);
        if (password != null) {
            pool = new JedisPool(new JedisPoolConfig(), host, port, Protocol.DEFAULT_TIMEOUT, password);
        } else {
            pool = new JedisPool(new JedisPoolConfig(), host, port, Protocol.DEFAULT_TIMEOUT);
        }
    }

    public void cleanup() throws DBException {
        pool.destroy();
    }

    /*
     * Calculate a hash for a key to store it in an index. The actual return value
     * of this function is not interesting -- it primarily needs to be fast and
     * scattered along the whole space of doubles. In a real world scenario one
     * would probably use the ASCII values of the keys.
     */
    private double hash(String key) {
        return key.hashCode();
    }

    // XXX jedis.select(int index) to switch to `table`

    @Override
    public Status read(String table, String key, Set<String> fields,
                       Map<String, ByteIterator> result) {
        try (Jedis jedis = pool.getResource()) {
            if (fields == null) {
                StringByteIterator.putAllAsByteIterators(result, jedis.hgetAll(key));
            } else {
                String[] fieldArray =
                        (String[]) fields.toArray(new String[fields.size()]);
                List<String> values = jedis.hmget(key, fieldArray);

                Iterator<String> fieldIterator = fields.iterator();
                Iterator<String> valueIterator = values.iterator();

                while (fieldIterator.hasNext() && valueIterator.hasNext()) {
                    result.put(fieldIterator.next(),
                            new StringByteIterator(valueIterator.next()));
                }
                assert !fieldIterator.hasNext() && !valueIterator.hasNext();
            }
            return result.isEmpty() ? Status.ERROR : Status.OK;
        }
    }

    @Override
    public Status insert(String table, String key,
                         Map<String, ByteIterator> values) {
        try (Jedis jedis = pool.getResource()) {
            if (jedis.hmset(key, StringByteIterator.getStringMap(values))
                    .equals("OK")) {
                jedis.zadd(INDEX_KEY, hash(key), key);
                return Status.OK;
            }
            return Status.ERROR;
        }

    }

    @Override
    public Status delete(String table, String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.del(key) == 0 && jedis.zrem(INDEX_KEY, key) == 0 ? Status.ERROR
                    : Status.OK;
        }
    }

    @Override
    public Status update(String table, String key,
                         Map<String, ByteIterator> values) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.hmset(key, StringByteIterator.getStringMap(values))
                    .equals("OK") ? Status.OK : Status.ERROR;
        }
    }

    @Override
    public Status scan(String table, String startkey, int recordcount,
                       Set<String> fields, Vector<HashMap<String, ByteIterator>> result) {
        try (Jedis jedis = pool.getResource()) {
            Set<String> keys = jedis.zrangeByScore(INDEX_KEY, hash(startkey),
                    Double.POSITIVE_INFINITY, 0, recordcount);

            HashMap<String, ByteIterator> values;
            for (String key : keys) {
                values = new HashMap<String, ByteIterator>();
                read(table, key, fields, values);
                result.add(values);
            }
            return Status.OK;
        }
    }

}
