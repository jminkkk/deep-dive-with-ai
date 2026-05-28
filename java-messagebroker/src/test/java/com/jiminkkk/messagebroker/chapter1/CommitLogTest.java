package com.jiminkkk.messagebroker.chapter1;

import com.jiminkkk.messagebroker.core.CommitLog;
import com.jiminkkk.messagebroker.model.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CommitLog의 핵심 동작을 검증한다.
 *
 * <p>학습 포인트:
 * <ol>
 *   <li>Append-Only: 레코드는 추가만 가능하다</li>
 *   <li>Offset: 각 레코드는 고유한 논리적 위치를 가진다</li>
 *   <li>Replay: 같은 offset으로 언제든 다시 읽을 수 있다</li>
 * </ol>
 */
class CommitLogTest {

    private CommitLog log;

    @BeforeEach
    void setUp() {
        log = new CommitLog();
    }

    @Test
    @DisplayName("append된 레코드는 순서대로 offset을 부여받는다")
    void append_AssignsSequentialOffsets() {
        // When
        long offset0 = log.append(Record.of("key1", "value1"));
        long offset1 = log.append(Record.of("key2", "value2"));
        long offset2 = log.append(Record.of("key3", "value3"));

        // Then
        assertThat(offset0).isEqualTo(0L);
        assertThat(offset1).isEqualTo(1L);
        assertThat(offset2).isEqualTo(2L);
    }

    @Test
    @DisplayName("read는 지정한 offset부터 maxRecords개를 반환한다")
    void read_ReturnsRecordsFromGivenOffset() {
        // Given
        log.append(Record.of(null, "A"));
        log.append(Record.of(null, "B"));
        log.append(Record.of(null, "C"));
        log.append(Record.of(null, "D"));

        // When: offset 1부터 2개 읽기
        List<Record> records = log.read(1, 2);

        // Then
        assertThat(records).hasSize(2);
        assertThat(records.get(0).value()).isEqualTo("B");
        assertThat(records.get(1).value()).isEqualTo("C");
    }

    @Test
    @DisplayName("Replay: 같은 offset으로 여러 번 읽어도 항상 동일한 레코드를 반환한다")
    void read_IsReplayable() {
        // Given
        log.append(Record.of(null, "hello"));
        log.append(Record.of(null, "world"));

        // When: 같은 offset을 두 번 읽음
        List<Record> first = log.read(0, 10);
        List<Record> second = log.read(0, 10);

        // Then: 결과가 동일 — Kafka는 같은 데이터를 언제든 재읽기 가능
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("범위를 벗어난 offset으로 읽으면 빈 리스트를 반환한다")
    void read_ReturnsEmptyForOutOfRangeOffset() {
        log.append(Record.of(null, "only-one"));

        assertThat(log.read(999, 10)).isEmpty();
        assertThat(log.read(-1, 10)).isEmpty();
    }

    @Test
    @DisplayName("append된 레코드의 offset은 읽어도 변하지 않는다 — Immutable Past")
    void append_RecordOffsetIsImmutable() {
        // Given
        long writtenOffset = log.append(Record.of("k", "v"));

        // When
        Record readRecord = log.read(writtenOffset, 1).get(0);

        // Then: 쓸 때 부여된 offset과 읽을 때 확인한 offset이 동일
        assertThat(readRecord.offset()).isEqualTo(writtenOffset);
        assertThat(readRecord.value()).isEqualTo("v");
    }

    @Test
    @DisplayName("nextOffset은 다음에 기록될 offset을 가리킨다")
    void nextOffset_ReflectsCurrentLogSize() {
        assertThat(log.nextOffset()).isEqualTo(0L);
        log.append(Record.of(null, "a"));
        assertThat(log.nextOffset()).isEqualTo(1L);
        log.append(Record.of(null, "b"));
        assertThat(log.nextOffset()).isEqualTo(2L);
    }
}
