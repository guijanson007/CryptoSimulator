SELECT time_stamp, price
FROM crypto_history ch
WHERE ch.crypto_id = :coin
  AND ch.time_stamp >= CURRENT_TIMESTAMP - INTERVAL '10 minutes'
ORDER BY ch.time_stamp ASC;
