val host = "http://localhost:8080"
val callbackUrl = "$host/vk/callback"

POST(callbackUrl) {
    contentType("application/json")
    body(
        """
       {
            "group_id": 232142875,
            "type": "message_new",
            "event_id": "fef1f1789eb2bd7c8cf147b5c7da926491320342",
            "v": "5.199",
            "object": {
                "client_info": {
                    "button_actions": [
                        "text",
                        "vkpay",
                        "open_app",
                        "location",
                        "open_link",
                        "open_photo",
                        "callback",
                        "intent_subscribe",
                        "intent_unsubscribe"
                    ],
                    "keyboard": true,
                    "inline_keyboard": true,
                    "carousel": true,
                    "lang_id": 0
                },
                "message": {
                    "date": 1756381457,
                    "from_id": 173308266,
                    "id": 0,
                    "version": 10000390,
                    "out": 0,
                    "fwd_messages": [],
                    "important": false,
                    "is_hidden": false,
                    "attachments": [],
                    "conversation_message_id": 2670,
                    "text": "[club232142875|@simarel] Ты как? Все хорошо?",
                    "is_unavailable": true,
                    "peer_id": 2000000001,
                    "random_id": 0
                }
            },
            "secret": "<secret>"
        }
        """.trimIndent(),
    )
}

POST(callbackUrl){
    contentType("application/json")
    body(
        """
       {
           "group_id": 232142875,
           "event_id": "2abd3fd8864e70eb085026e790635ef25067b93c",
           "v": "5.199",
           "type": "confirmation",
           "secret": "<secret>"
       }
        """.trimIndent(),
    )
}

POST(callbackUrl){
    contentType("application/json")
    body(
        """
       {
           "group_id": 232142875,
           "event_id": "2abd3fd8864e70eb085026e790635ef25067b93c",
           "v": "5.199",
           "type": "bla-bla-bla",
           "secret": "<secret>"
       }
        """.trimIndent(),
    )
}

POST(callbackUrl) {
    contentType("application/json")
    body(
        """
   {
       "group_id": 232142875,
       "event_id": "2abd3fd8864e70eb085026e790635ef25067b93c",
       "v": "5.199",
       "type": "bla-bla-bla",
       "secret": "incorrect secret"
   }
    """.trimIndent(),
    )
}