(***************************************************)
(*               Associated library                *)
(***************************************************)
library ZilGame

let andb = 
  fun (b : Bool) => fun (c : Bool) =>
    match b with 
    | False => False
    | True  => match c with 
               | False => False
               | True  => True
               end
    end

let orb = 
  fun (b : Bool) => fun (c : Bool) =>
    match b with 
    | True  => True
    | False => match c with 
               | False => False
               | True  => True
               end
    end

let negb = fun (b : Bool) => 
  match b with
  | True => False
  | False => True
  end

let one_msg = 
  fun (msg : Message) => 
   let nil_msg = Nil {Message} in
   Cons {Message} msg nil_msg

let no_msg = Nil {Message}

let update_hash = 
  fun (oh : Option Hash) =>
  fun (h : Hash) =>
  match oh with
  | Some x => Some {Hash} x
  | None   => Some {Hash} h
  end

let update_timer = 
  fun (tm : Option BNum) =>
  fun (b : BNum) =>
  match tm with
  | Some x => Some {BNum} x
  | None   =>
    let window = 11 in
    let b1 = builtin badd b window in
    Some {BNum} b1
  end

(* b is within the time window *)
let can_play = 
  fun (tm : Option BNum) =>
  fun (b : BNum) =>
  match tm with
  | None => True
  | Some b1 => builtin blt b b1
  end     

let time_to_claim = 
  fun (tm : Option BNum) =>
  fun (b : BNum) =>
  match tm with
  | None => False
  | Some b1 =>
    let c1 = builtin blt b b1 in
    negb c1
  end     

let check_validity = 
  fun (a        : Address) =>
  fun (solution : Int) =>
  fun (pa       : Address) =>
  fun (pb       : Address) =>
  fun (guess_a  : Option Hash) =>
  fun (guess_b  : Option Hash) =>
  let ca = builtin eq pa a in
  let cb = builtin eq pb a in
  let xa = And {Bool (Option Hash)} ca guess_a in 
  let xb = And {Bool (Option Hash)} cb guess_b in 
  match xa with
  | And True (Some g) =>
    let h = builtin sha256hash solution in
    builtin eq h g 
  | _ =>
    match xb with
    | And True (Some g) =>
      let h = builtin sha256hash solution in
      builtin eq h g
    | _ => False  
    end  
  | _ => False
  end  

(* In the case of equal results, or no results the prise goes to the owner *)
let determine_winner = 
  fun (puzzle   : Hash) =>
  fun (guess_a  : Option Hash) =>
  fun (guess_b  : Option Hash) =>
  fun (pa       : Address) =>
  fun (pb       : Address) =>
  fun (oa       : Address) =>
  let gab = And { (Option Hash) (Option Hash) } guess_a guess_b in
  match gab with
  | And (Some ga) (Some gb) =>
    let d1 = builtin dist puzzle ga in
    let d2 = builtin dist puzzle gb in
    let c1 = builtin lt d1 d2 in
    match c1 with 
    | True => pa
    | False => 
      let c2 = builtin eq d1 d2 in
      match c2 with 
      | False => pb
      | True  => oa
      end
    end
  | And (Some _) None => pa
  | And None (Some _) => pb
  | And None None     => oa
  end

let solution_submitted = 1
let time_window_missed = 2
let not_a_player = 3  
let too_early_to_claim = 4
let wrong_sender_or_solution = 5
let here_is_the_reward = 6

(***************************************************)
(*             The contract definition             *)
(***************************************************)
contract ZilGame 
  (owner    : Address,
   player_a : Address,
   player_b : Address,
   puzzle   : Hash)

(* Initial balance is not stated explicitly: it's initialized when creating the contract. *)

field player_a_hash : Option Hash = None {Hash}
field player_b_hash : Option Hash = None {Hash}
field timer         : Option BNum  = None {BNum}
field game_on       : Bool = False

transition Play (sender: Address, guess: Hash)
  tm_opt <- timer;
  b <- & BLOCKNUMBER;
  (* Check the timer *)
  c = can_play tm_opt b;
  match c with
  | False => 
      msg  = {tag : Main; to : sender; amount : 0; 
              code : time_window_missed};
      msgs = one_msg msg;
      send msgs        
  | True  => 
    isa = builtin eq sender player_a;
    isb = builtin eq sender player_b;
    tt = True;
    match isa with
    | True =>
      game_on := t;
      ah <- player_a_hash;
      hopt = update_hash ah guess;
      player_a_hash := hopt;
      tm1 = update_timer tm_opt b;
      timer := tm1;
      msg  = {tag : Main; to : sender; amount : 0; 
              code : solution_submitted};
      msgs = one_msg msg;
      send msgs        
    | False =>
      match isb with 
      | True =>
        game_on := tt;
        bh <- player_b_hash;
        hopt = update_hash bh guess;
        player_b_hash := hopt;
        tm1 = update_timer tm_opt b;
        timer := tm1;
        msg  = {tag : Main; to : sender; amount : 0; 
                code : solution_submitted};
        msgs = one_msg msg;
        send msgs        
      | False => 
        msg  = {tag : Main; to : sender; amount : 0; 
                code : not_a_player};
        msgs = one_msg msg;
        send msgs
      end	
    end
  end
end

transition ClaimReward
  (sender: Address, solution: Int)
  tm_opt <- timer;
  b <- & BLOCKNUMBER;
  (* Check the timer *)
  ttc = time_to_claim tm_opt b;
  match ttc with
  | False => 
      msg  = {tag : Main; to : sender; amount : 0; 
              code : too_early_to_claim};
      msgs = one_msg msg;
      send msgs        
  | True  => 
    pa <- player_a_hash;
    pb <- player_b_hash;
    is_valid = check_validity sender solution player_a player_b pa pb;
    match is_valid with
    | False =>
      msg  = {tag : Main; to : sender; amount : 0; 
              code : wrong_sender_or_solution};
      msgs = one_msg msg;
      send msgs        
    | True  =>
      winner = determine_winner puzzle pa pb player_a player_b owner; 
      bal <- & BALANCE;
      msg  = {tag : Main; to : winner; amount : bal; 
              code : here_is_the_reward};
      ff = False;	       
      game_on := ff;
      msgs = one_msg msg;
      send msgs
    end
  end
end


