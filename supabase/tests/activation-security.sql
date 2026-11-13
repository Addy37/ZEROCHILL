-- Disposable Migration Test: read-only assertions, no user data output.
begin;
do $$
declare r text; t text;
begin
    foreach r in array array['anon','authenticated'] loop
        assert not has_table_privilege(r,'private.legacy_account_mapping','SELECT');
        assert not has_table_privilege(r,'private.legacy_account_mapping','INSERT');
        assert not has_table_privilege(r,'private.legacy_account_mapping','UPDATE');
        assert not has_table_privilege(r,'private.legacy_account_mapping','DELETE');
        assert not has_function_privilege(r,'public.register_legacy_account(uuid,uuid)','EXECUTE');
        assert not has_function_privilege(r,'public.begin_legacy_claim(uuid,uuid,uuid)','EXECUTE');
        assert not has_function_privilege(r,'public.finish_legacy_claim(uuid,uuid,boolean)','EXECUTE');
    end loop;
    assert not has_function_privilege('anon','public.legacy_activation_status()','EXECUTE');
    assert has_function_privilege('authenticated','public.legacy_activation_status()','EXECUTE');
    assert not (select prosecdef from pg_proc where oid='public.legacy_activation_status()'::regprocedure);
    assert not has_table_privilege('service_role','auth.sessions','SELECT');
    assert has_column_privilege('service_role','auth.sessions','id','SELECT');
    assert has_column_privilege('service_role','auth.sessions','user_id','SELECT');
    assert has_column_privilege('service_role','auth.sessions','not_after','SELECT');
    foreach t in array array['profiles','creator_favorites','comments','comment_likes',
        'video_likes','user_blocks','direct_messages','notification_preferences',
        'social_reports','push_device_tokens'] loop
        if to_regclass('public.'||t) is not null then
            assert exists (select 1 from pg_policies p where p.schemaname='public'
                and p.tablename=t and p.policyname='legacy_activation_required' and p.permissive='RESTRICTIVE');
        end if;
    end loop;
    assert exists (select 1 from pg_policies where schemaname='storage' and tablename='objects'
        and policyname='legacy_activation_required' and permissive='RESTRICTIVE');
end;
$$;
rollback;
