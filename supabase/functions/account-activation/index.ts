import {handleActivation} from './handler.ts';
Deno.serve((req: Request) => handleActivation(req, {
  url:Deno.env.get('SUPABASE_URL') ?? '',
  anon:Deno.env.get('SUPABASE_ANON_KEY') ?? '',
  service:Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '',
}));
