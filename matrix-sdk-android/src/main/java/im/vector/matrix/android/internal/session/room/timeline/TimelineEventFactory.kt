/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.matrix.android.internal.session.room.timeline

import im.vector.matrix.android.api.session.crypto.CryptoService
import im.vector.matrix.android.api.session.events.model.EventType
import im.vector.matrix.android.api.session.room.timeline.TimelineEvent
import im.vector.matrix.android.internal.database.mapper.asDomain
import im.vector.matrix.android.internal.database.model.EventEntity
import im.vector.matrix.android.internal.session.room.members.SenderRoomMemberExtractor
import io.realm.Realm
import timber.log.Timber

internal class TimelineEventFactory(private val roomMemberExtractor: SenderRoomMemberExtractor,
                                    private val cryptoService: CryptoService) {

    private val cached = mutableMapOf<String, SenderData>()

    fun create(eventEntity: EventEntity, realm: Realm = eventEntity.realm): TimelineEvent {
        val sender = eventEntity.sender
        val cacheKey = sender + eventEntity.stateIndex
        val senderData = cached.getOrPut(cacheKey) {
            val senderRoomMember = roomMemberExtractor.extractFrom(eventEntity, realm)
            SenderData(senderRoomMember?.displayName, senderRoomMember?.avatarUrl)
        }
        val event = eventEntity.asDomain()
        if (event.getClearType() == EventType.ENCRYPTED) {
            try {
                val result = cryptoService.decryptEvent(event, "TODO")
                event.setClearData(result)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
        return TimelineEvent(
                event,
                eventEntity.localId,
                eventEntity.displayIndex,
                senderData.senderName,
                senderData.senderAvatar,
                eventEntity.sendState
        )
    }

    fun clear() {
        cached.clear()
    }

    private data class SenderData(
            val senderName: String?,
            val senderAvatar: String?
    )

}