-- KEYS[1]: stock:product:{productId}:remaining
-- KEYS[2]: stock:product:{productId}:holders
-- ARGV[1]: checkoutTokenId
-- return: 1 released, 0 no allocation record

if redis.call('HEXISTS', KEYS[2], ARGV[1]) == 0 then
    return 0
end

redis.call('HDEL', KEYS[2], ARGV[1])
redis.call('INCR', KEYS[1])
return 1
