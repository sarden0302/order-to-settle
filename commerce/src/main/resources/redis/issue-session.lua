-- 로그인 세션 발급. 계정 가드가 ACTIVE이고 인증 버전이 DB에서 확인한 값과 같을 때만 저장한다.
-- 조건을 모두 확인한 뒤 마지막에 쓴다. docs/data-model.md#redis-인증-데이터
-- KEYS[1]: commerce-settle:auth:session:<epoch>:<tokenHash>
-- KEYS[2]: commerce-settle:auth:subject:<epoch>:<actorKind>:<actorId>
-- ARGV[1]: actorKind, ARGV[2]: actorId, ARGV[3]: DB auth_version, ARGV[4]: 세션 수명(ms)
-- 반환: {'ISSUED', issuedAt, expiresAt} | {'REJECTED'} | {'UNAVAILABLE', 사유}
local guard = redis.call('HMGET', KEYS[2], 'authVersion', 'state', 'pendingChangeId')
local version, state, pending = guard[1], guard[2], guard[3]
local createGuard = false
if not version and not state then
  -- 최초 로그인은 DB에서 확인한 상태·버전으로 가드를 만든다. 기존 BLOCKED 가드는 로그인으로 열지 않는다.
  createGuard = true
  version, state = ARGV[3], 'ACTIVE'
elseif not version or not state then
  return {'UNAVAILABLE', 'GUARD_INCOMPLETE'}
end
if pending then
  return {'UNAVAILABLE', 'CHANGE_PENDING'}
end
if state ~= 'ACTIVE' or version ~= ARGV[3] then
  return {'REJECTED'}
end
if redis.call('EXISTS', KEYS[1]) == 1 then
  return {'UNAVAILABLE', 'TOKEN_COLLISION'}
end
local now = redis.call('TIME')
local issuedAt = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
local expiresAt = issuedAt + tonumber(ARGV[4])
if createGuard then
  redis.call('HSET', KEYS[2], 'authVersion', version, 'state', state)
end
redis.call('HSET', KEYS[1], 'actorKind', ARGV[1], 'actorId', ARGV[2], 'purpose', 'SESSION',
  'authVersion', ARGV[3], 'issuedAt', issuedAt, 'expiresAt', expiresAt)
redis.call('PEXPIREAT', KEYS[1], expiresAt)
return {'ISSUED', tostring(issuedAt), tostring(expiresAt)}
