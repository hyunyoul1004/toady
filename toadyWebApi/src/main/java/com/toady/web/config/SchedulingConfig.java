package com.toady.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 앱 전체의 @Scheduled 작업(시세 미리 계산 PriceGapSnapshots, 일정 알림 PlannerReminder, 푸시 묶음 PushDigest,
 * ContentStore·IndexNowNotifier 등)을 한곳에서 켠다. 예전에는 몇몇 클래스 안쪽에 같은 설정이 흩어져 있었다.
 * 알림 메일·푸시처럼 켜고 끌 작업은 각 클래스의 @ConditionalOnProperty로 빈 자체를 만들지 않아 스케줄도 돌지 않는다.
 * 스레드 수는 application.yml의 spring.task.scheduling.pool.size로 정한다.
 */
// @Configuration: 설정 클래스. @EnableScheduling: @Scheduled가 붙은 메서드를 예약 실행하게 한다
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
