alter table public.push_device_tokens
    rename column fcm_token to firebase_installation_id;

alter table public.push_device_tokens
    rename constraint push_device_tokens_fcm_token_length_check
    to push_device_tokens_firebase_installation_id_length_check;

alter table public.push_device_tokens
    rename constraint push_device_tokens_fcm_token_key
    to push_device_tokens_firebase_installation_id_key;
