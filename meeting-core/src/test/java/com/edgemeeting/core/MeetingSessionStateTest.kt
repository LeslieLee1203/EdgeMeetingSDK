package com.edgemeeting.core

import com.edgemeeting.core.model.MeetingState
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MeetingSessionStateTest {

    // 使用 TestScope 來控制虛擬時間
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `prepare should transition state from Idle to Ready`() = testScope.runTest {
        // Arrange
        val session = FakeMeetingSession(this) // 把 TestScope 傳進去

        // Assert Initial State
        assertEquals("一開始應該是 Idle", MeetingState.Idle, session.state.value)

        // Act
        session.prepare()

        // Assert Intermediate State
        assertEquals("呼叫後應該立刻變成 Preparing", MeetingState.Preparing, session.state.value)

        // 前進虛擬時間 (因為 Fake 裡面有 delay 100ms)
        advanceUntilIdle()

        // Assert Final State
        assertEquals("載入完成後應該變成 Ready", MeetingState.Ready, session.state.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `start should only work when Ready`() = testScope.runTest {
        // Arrange
        val session = FakeMeetingSession(this)

        // Act: 直接 Start (還沒 Prepare)
        session.start()

        // Assert: 應該還是在 Idle，因為沒 Ready 不能 Start
        assertEquals(MeetingState.Idle, session.state.value)

        // Act: 乖乖 Prepare
        session.prepare()
        advanceUntilIdle() // 等它 Ready
        session.start()

        // Assert: 終於變成 Listening
        assertEquals(MeetingState.Listening, session.state.value)
    }
}