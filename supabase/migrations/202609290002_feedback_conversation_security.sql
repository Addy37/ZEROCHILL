create or replace function public.touch_feedback_from_message()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
    update public.app_feedback
       set updated_at = greatest(updated_at, new.created_at)
     where id = new.feedback_id;
    return new;
end;
$$;
