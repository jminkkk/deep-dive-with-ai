package com.jiminkkk.messagebroker.core;

import com.jiminkkk.messagebroker.model.Record;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Kafka의 핵심 자료구조 — 추가 전용(Append-Only) 로그.
 *
 * <p>Kafka의 모든 데이터는 이 로그 구조로 저장된다.
 * 로그는 오직 끝에만 레코드를 추가할 수 있고(append), 수정이나 삭제는 없다.
 * 이 단순한 제약이 Kafka의 높은 처리량과 내구성의 근원이다.
 *
 * <p>핵심 특성:
 * <ul>
 *   <li><b>Offset</b>: 각 레코드의 논리적 위치. 0부터 단조 증가한다.</li>
 *   <li><b>순차 쓰기</b>: 랜덤 I/O 없이 디스크 끝에만 쓰므로 HDD에서도 빠르다.</li>
 *   <li><b>불변 과거</b>: 한 번 기록된 레코드는 변경되지 않는다. 재읽기(replay)가 가능하다.</li>
 * </ul>
 *
 * <p>교육용 단순화: 실제 Kafka는 세그먼트(Segment) 파일로 로그를 분할하고
 * 인덱스 파일로 offset → 파일 위치 매핑을 관리한다.
 * 여기서는 ArrayList로 단순화했다.
 *
 * @see <a href="https://kafka.apache.org/documentation/#log">Kafka Log</a>
 */
public class CommitLog {

    // 교육용 단순화: 실제는 세그먼트 파일 배열 + 인덱스 파일
    private final List<Record> records = new ArrayList<>();
    private long nextOffset = 0;

    /**
     * 레코드를 로그 끝에 추가하고 부여된 offset을 반환한다.
     *
     * <p>이 연산은 항상 로그의 끝(tail)에만 수행된다 — Append-Only.
     */
    public synchronized long append(Record record) {
        long offset = nextOffset++;
        records.add(record.withOffset(offset));
        return offset;
    }

    /**
     * 지정한 offset부터 최대 maxRecords개의 레코드를 읽는다.
     *
     * <p>Kafka Consumer의 fetch 동작에 해당한다.
     * offset이 유효 범위를 벗어나면 빈 리스트를 반환한다.
     *
     * @param fromOffset 읽기 시작할 offset (inclusive)
     * @param maxRecords 최대로 반환할 레코드 수
     * @return 읽은 레코드 목록 (불변)
     */
    public synchronized List<Record> read(long fromOffset, int maxRecords) {
        if (fromOffset < 0 || fromOffset >= records.size()) {
            return Collections.emptyList();
        }
        int from = (int) fromOffset;
        int to = (int) Math.min(from + maxRecords, records.size());
        return Collections.unmodifiableList(records.subList(from, to));
    }

    /** 현재 로그에 기록된 레코드 수 */
    public synchronized long size() {
        return records.size();
    }

    /** 다음에 부여될 offset (= 현재 로그 크기) */
    public synchronized long nextOffset() {
        return nextOffset;
    }
}
