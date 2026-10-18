-- Fills Crypto_History with :days days of history at each default coin's real bot rate
-- (one trade every 5 / volatility seconds), ending now. Prices are seeded random noise.
INSERT INTO Cryptocurrency (symbol, name, current_price, original_price, volatility)
VALUES ('BTC', 'Bitcoin', 60000, 60000, 2.0), ('ETH', 'Ethereum', 3000, 3000, 1.5), ('LTC', 'Litecoin', 80, 80, 3.5),
       ('XRP', 'Ripple', 0.5, 0.5, 3.0),       ('ADA', 'Cardano', 0.4, 0.4, 0.5)
ON CONFLICT (symbol) DO NOTHING;   -- Crypto_History rows reference these
DROP INDEX IF EXISTS crypto_history_coin_time;   -- TRUNCATE keeps indexes; every configuration starts from the PK only
TRUNCATE Crypto_History;
SELECT setseed(0.42);
INSERT INTO Crypto_History (crypto_id, time_stamp, price)
SELECT c.symbol,
       now() - (g.i * s.step) * INTERVAL '1 second',
       c.base * (1 + 0.05 * (random() - 0.5))
FROM (VALUES ('BTC', 2.0, 60000.0), ('ETH', 1.5, 3000.0), ('LTC', 3.5, 80.0),
             ('XRP', 3.0, 0.5),     ('ADA', 0.5, 0.4)) AS c(symbol, volatility, base)
CROSS JOIN LATERAL (SELECT 5.0 / c.volatility AS step) s
CROSS JOIN LATERAL generate_series(0, floor(:days * 86400 / s.step)::int - 1) AS g(i)
ORDER BY 2;   -- insert oldest first, so event_id grows with time and coins interleave, as in production
VACUUM ANALYZE Crypto_History;
