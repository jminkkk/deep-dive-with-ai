package com.jiminkkk.virtualthread.chapter5;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Virtual Thread 환경에서 ThreadLocal 캐싱 패턴의 함정을 보여준다.
 *
 * <p>플랫폼 스레드 풀에서 ThreadLocal 캐싱이 효과적인 이유:
 * <ul>
 *   <li>스레드 풀의 스레드 수가 고정 → ThreadLocal 인스턴스 수도 고정</li>
 *   <li>스레드가 재사용 → 같은 ThreadLocal 인스턴스를 여러 작업이 재사용</li>
 * </ul>
 *
 * <p>Virtual Thread에서 ThreadLocal 캐싱이 문제가 되는 이유:
 * <ul>
 *   <li>VT는 작업마다 새로 생성되고, 작업 완료 후 GC 대상이 된다</li>
 *   <li>→ ThreadLocal 인스턴스가 작업 수만큼 생성 → 메모리 압박</li>
 *   <li>→ 캐싱 효과 없음 — 생성 즉시 GC 대상</li>
 * </ul>
 *
 * <p>권장 대안:
 * <ol>
 *   <li>불변(immutable) 공유 객체 사용 ({@code DateTimeFormatter} 등)</li>
 *   <li>Java 21 Preview: {@code ScopedValue} — 스레드 계층에 값 전달에 적합</li>
 *   <li>메서드 로컬 변수 — 단순 계산이면 ThreadLocal이 필요 없음</li>
 * </ol>
 *
 * @see <a href="https://openjdk.org/jeps/444#Thread-local-variables">JEP 444: Thread-local variables</a>
 */
public class ThreadLocalTrap {

    /**
     * ❌ 안티패턴: VT 환경에서 ThreadLocal로 가변 객체 캐싱.
     * VT가 재사용되지 않으므로 캐싱 효과가 없고, 인스턴스만 늘어난다.
     */
    private static final ThreadLocal<StringBuilder> CACHED_BUILDER =
            ThreadLocal.withInitial(StringBuilder::new);

    /** ThreadLocal.withInitial()이 실제로 몇 번 호출됐는지 추적 */
    private final AtomicLong newInstanceCount = new AtomicLong();

    /**
     * ThreadLocal로 StringBuilder를 캐싱하는 방식.
     * VT 환경에서는 사실상 매 작업마다 새 인스턴스가 만들어진다.
     */
    public String buildWithCachedThreadLocal(String value) {
        StringBuilder sb = CACHED_BUILDER.get();
        if (sb.length() == 0) {
            // ThreadLocal이 새로 초기화된 경우 (= 새 VT에서 처음 get())
            newInstanceCount.incrementAndGet();
        }
        sb.setLength(0);
        return sb.append("result: ").append(value).toString();
    }

    /**
     * ✅ 권장 패턴: 매번 새 인스턴스 생성.
     * VT 환경에서는 ThreadLocal 캐싱보다 이 방식이 더 단순하고 메모리 효율적이다.
     */
    public String buildWithNewInstance(String value) {
        return new StringBuilder().append("result: ").append(value).toString();
    }

    /**
     * @return ThreadLocal 신규 초기화 횟수 (= 캐싱 미스 횟수)
     */
    public long getNewInstanceCount() {
        return newInstanceCount.get();
    }
}
