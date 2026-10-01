package com.zth.java.ai.langchain4j.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** MongoDB 消息文档：保存会话标识和序列化后的消息窗口。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Document("chat_messages")
public class ChatMessages {

    @Id
    private ObjectId messageId;

    private String memoryId;

    private String content;
}
