-- New Zerochill: keep social reads light while preserving every RLS decision.
-- These are indexes for existing foreign-key lookups and per-statement Auth
-- evaluation for DM reaction policies. No client-visible behavior changes.
create index if not exists conversation_clear_state_partner_id_idx
  on public.conversation_clear_state (partner_id);

create index if not exists source_config_versions_based_on_version_idx
  on public.source_config_versions (based_on_version);

alter policy "participants read reactions"
on public.dm_reactions
using (
  exists (
    select 1 from public.direct_messages dm
    where dm.id = dm_reactions.message_id
      and ((select auth.uid()) = dm.sender_id or (select auth.uid()) = dm.recipient_id)
  )
);

alter policy "own participant reaction insert"
on public.dm_reactions
with check ((select auth.uid()) = user_id and private.can_react_dm(message_id));

alter policy "own participant reaction update"
on public.dm_reactions
using ((select auth.uid()) = user_id and private.can_react_dm(message_id))
with check ((select auth.uid()) = user_id and private.can_react_dm(message_id));

alter policy "own participant reaction delete"
on public.dm_reactions
using ((select auth.uid()) = user_id and private.can_react_dm(message_id));
