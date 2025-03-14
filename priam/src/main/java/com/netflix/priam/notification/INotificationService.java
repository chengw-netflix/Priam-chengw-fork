/**
 * Copyright 2017 Netflix, Inc.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.priam.notification;

import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.google.inject.ImplementedBy;
import java.util.Map;

/** Service to notify of a message. Created by vinhn on 11/3/16. */
@ImplementedBy(AWSSnsNotificationService.class)
public interface INotificationService {
    /**
     * Notify the message.
     *
     * @param msg Message that needs to be notified
     * @param messageAttributes Message attributes to be used while sending the message.
     */
    void notify(String msg, Map<String, MessageAttributeValue> messageAttributes);
}
