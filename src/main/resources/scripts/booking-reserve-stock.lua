-- KEYS[1]: stock:product:{productId}:remaining
-- KEYS[2]: stock:product:{productId}:holders
-- ARGV[1]: checkoutTokenId
-- ARGV[2]: reservedAt
-- return: 1 success, 0 sold out, 2 duplicated request, -1 missing remaining key

if redis.call('HEXISTS', KEYS[2], ARGV[1]) == 1 then
    return 2
end

local remaining = redis.call('GET', KEYS[1])
if not remaining then
    return -1
end

remaining = tonumber(remaining)
if remaining <= 0 then
    return 0
end

redis.call('DECR', KEYS[1])
redis.call('HSET', KEYS[2], ARGV[1], ARGV[2])
return 1
