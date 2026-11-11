-- 로그아웃. Gateway가 검증해 전달한 계정의 현재 세션만 조건부 삭제한다. 다른 계정 세션은 지우지 않는다.
-- KEYS[1]: commerce-settle:auth:session:<epoch>:<tokenHash>
-- ARGV[1]: actorKind, ARGV[2]: actorId
-- 반환: 1 삭제, 0 없음 또는 다른 계정의 세션
local session = redis.call('HMGET', KEYS[1], 'actorKind', 'actorId', 'purpose')
if session[1] ~= ARGV[1] or session[2] ~= ARGV[2] or session[3] ~= 'SESSION' then
  return 0
end
return redis.call('DEL', KEYS[1])
