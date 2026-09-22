-- Atomic Token Bucket Rate Limiter with Burst Support
-- KEYS[1]: State key (e.g., rate_limit:{tenant_id})
-- ARGV[1]: replenish_rate (tokens added per second)
-- ARGV[2]: capacity (maximum burst capacity)
-- ARGV[3]: now (current timestamp in epoch seconds with fractional precision)
-- ARGV[4]: requested_tokens (tokens requested, default 1)


local rate_limit_key = KEYS[1]
local replenish_rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

if requested == nil or requested <= 0 then 
    requested = 1
end

-- Retrieve current state: [tokens, last_refreshed]

local state = redis.call('HMGET', rate_limit_key, 'tokens', last_refreshed)
local current_tokens = tonumber(state[1])
local last_refreshed = tonumber(state[2])

if current_tokens == nil or last_refreshed == nil then
    --first time access for this bucket : start at full capacity
    current_tokens = capacity
    last_refreshed = now
else 
    --Compute refill based on elapsed time
    local delta = math.max(0, now - last_refreshed)
    local tokens_to_add = delta * replenish_rate
    current_tokens = math.min(capacity, current_tokens + tokens_to_add)
    last_refreshed = now
end

-- Evaluate token avilability
local allowed = 0
local remaining = 0
local wait_or_reset_time = 0

if current_tokens >= requested then
    allowed = 1
    current_tokens = current_tokens - requested
    remaining = math.floor(current_tokens)
    -- Reset time : seconds until bucket is completely full again
    if replenish_rate > 0 then 
        wait_or_reset_time = math.ceil((capacity - current_tokens) / replenish_rate)
    else
        wait_or_reset_time = 0
    end
else 
    allowed = 0
    remaining = math.floor(current_tokens)
    --Retry after: seconds until at least 'requested' tokens are refilled
    local deficit = requested - current_tokens
    if replenish_rate > 0 then 
        wait_or_reset_time = math.ceil(deficit / replenish_rate)
        if wait_or_reset_time < 1 then 
            wait_or_reset_time = 1
        end 
    else
        wait_or_reset_time = 60
    end
end

-- Update state in redis
redis.call('HMSET', rate_limit_key, 'tokens', tostring(current_tokens), 'last_refreshed', tostring(last_refreshed))

-- Set TTL  to ensure unused keys expire automatically (2x fill time, at least 60 secconds)

local ttl = 60
if replenish_rate > 0 then 
    ttl = math.max(60, math.ceil((capacity / replenish_rate) * 2))
end 

redis.call('EXPIRE', rate_limit_key, ttl)

-- return : {allowed (1 or 0), remaining_tokens, wait_or_reset_time}
return {allowed , remaining, wait_or_reset_time}