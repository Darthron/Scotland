package uk.ac.bris.cs.scotlandyard.model;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import java.util.ListIterator;
import java.util.function.Consumer;

import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Node;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYardPlayer;

public class ScotlandYardModel implements ScotlandYardGame, Consumer <Move> {

	private List <Boolean> 						rounds;
	private Graph <Integer, Transport> 			graph;
	private ScotlandYardPlayer					mrX;
	private LinkedList <Spectator>				spectators;
	private ListIterator <ScotlandYardPlayer>	currentPlayer;
	private LinkedList <ScotlandYardPlayer>		players;
	private LinkedList <Colour>                 winningPlayersColours;
	private int									currentRound;
	private int									mrXLastKnownLocation;
	private boolean                             currentRotationComplete;

	public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
			PlayerConfiguration mrX, PlayerConfiguration firstDetective,
			PlayerConfiguration... restOfTheDetectives) {
		
		LinkedList <PlayerConfiguration> detectivePlayers = new LinkedList <PlayerConfiguration> ();
		Set <Integer> locations = new HashSet <> ();
		Set <Colour> colours = new HashSet <> ();

		// Checks if rounds is null or empty
		this.rounds = Objects.requireNonNull(rounds);
		if (rounds.isEmpty())
		{
			throw new IllegalArgumentException("Empty rounds");
		}

		// Checks if graph is null or empty
		this.graph = Objects.requireNonNull(graph);
		if (graph.isEmpty())
		{
			throw new IllegalArgumentException("Empty graph");
		}

		// Check if mrX is null
		this.mrX = new ScotlandYardPlayer(Objects.requireNonNull(mrX).player,
									  	  mrX.colour,
									  	  mrX.location,
									  	  mrX.tickets);	
		// Check if mrX has colour Black
		if (! isMrX(mrX))
		{
			throw new IllegalArgumentException("MrX must have colour Black");
		}

        // Initialise players list
        players = new LinkedList <ScotlandYardPlayer> ();
		
        // Initialise winningPlayersColours
        winningPlayersColours = new LinkedList <Colour> ();

		// Add MrX to the players list										  
		players.add(this.mrX);	

		// Check mrX tickets
		checkPlayerTickets(mrX);

		// Add the location and colour of mrX to a set in order to check
		// for duplicates of locations and colours among the players
		locations.add(mrX.location);
		colours.add(mrX.colour);
		
		// Build the list of detectives for to check more easily for null and
		// location/colour duplicates
		detectivePlayers.add(firstDetective);
		for (PlayerConfiguration currentDetective : restOfTheDetectives)
		{
			detectivePlayers.add(currentDetective);
		}
			
		// Check the detectives for null and duplicate location/colour
		for (PlayerConfiguration currentDetective : detectivePlayers)
		{
			if (null == currentDetective)
			{
				throw new NullPointerException();
			}

			// If the location of the detective is already occupied
			if (locations.contains(currentDetective.location))
			{
				throw new IllegalArgumentException("Duplicate location");	
			}

			// If the colour of the detective is already used
			if (colours.contains(currentDetective.colour))
			{
				throw new IllegalArgumentException("Duplicate colour");
			}

			checkPlayerTickets(currentDetective);

			players.add(new ScotlandYardPlayer(currentDetective.player,
											   currentDetective.colour,
											   currentDetective.location,
											   currentDetective.tickets));
			locations.add(currentDetective.location);
			colours.add(currentDetective.colour);
		}
		
		// Make current player the first player in the players list
		currentPlayer = players.listIterator(0);	

		// Initialise the spectator list
		spectators = new LinkedList <Spectator> ();

		currentRound = 0;
		mrXLastKnownLocation = 0;
		currentRotationComplete = false;
	}

	// Input: spectator (Spectator)
	// Preconditions: spectator is not null and is not already registered
	// Output: spectators (LinkedList <Spectator>
	// Postconditions: List of spectators that contains the spectator we've just added
	@Override
	public void
	registerSpectator(Spectator spectator) {
	    if (null == spectator)
	    {
	        throw new NullPointerException("Null spectator");
	    }

		if (spectators.contains(spectator))
		{
			throw new IllegalArgumentException("Spectator already exists");
		}
		
		spectators.add(spectator);
	}

	
	// Input: spectator (Spectator)
	// Preconditions: spectator is not null and is registered
	// Output: spectators (LinkedList <Spectator>
	// Postconditions: List of spectators that dose not contain the spectator passed as argument
	@Override
	public void
	unregisterSpectator(Spectator spectator) {
	    if (null == spectator)
	    {
	        throw new NullPointerException("Null spectator");
	    }

		if (! spectators.remove(spectator))
		{
			throw new IllegalArgumentException("Spectator does not exist");
		}
	}

	// 
	@Override
	public void
	startRotate() {
		ScotlandYardPlayer playerToMove;

		playerToMove = getPlayer(getCurrentPlayer());

        if (isGameOver())
        {
            throw new IllegalStateException("Game is already over");
        }

		playerToMove.player().makeMove(this,
							  		   playerToMove.location(),
									   getAvailableMoves(playerToMove),
							  		   this);
	}

	@Override
	public void
	accept(Move move)
	{
		HashSet <Move> 		availableMoves;
		Move                hiddenMove;
		ScotlandYardPlayer 	player;

		if (null == move)
		{
			throw new NullPointerException();
		}
        
        // Get current player
		player = getPlayer(getCurrentPlayer());

		availableMoves = (HashSet <Move>) getAvailableMoves(player);
		if (! availableMoves.contains(move))
		{
			throw new IllegalArgumentException();
		}

        if (isMrX(player))
        {
		    hiddenMove = hideMoveIfNotRevealRound(move);
		}
		else
		{
		    hiddenMove = move;
		}

		// Let spectators know about the made move
		notifySpectatorsMoveMade(hiddenMove);

		// Notify spectators about started round if mrX moves
		if (isMrX(player))
		{
		    currentRound++;
		    notifySpectatorsRoundStarted();
	    }

		// Update the player's location and tickets
		if (move instanceof TicketMove)
		{
			player.location(((TicketMove) move).destination());
			player.tickets().replace(((TicketMove) move).ticket(),
									 player.tickets().get(((TicketMove) move).ticket()) - 1);
			if (! isMrX(player))
			{
			    mrX.tickets().replace(((TicketMove) move).ticket(),
								      mrX.tickets().get(((TicketMove) move).ticket()) + 1);
			}
		}
		else if (move instanceof DoubleMove)
		{
			player.location(((DoubleMove) move).finalDestination());
			player.tickets().replace(((DoubleMove) move).firstMove().ticket(),
									 player.tickets().get(((DoubleMove) move).firstMove().ticket()) - 1);
			player.tickets().replace(((DoubleMove) move).secondMove().ticket(),
									 player.tickets().get(((DoubleMove) move).secondMove().ticket()) - 1);
			player.tickets().replace(Ticket.valueOf("Double"),
									 player.tickets().get(Ticket.valueOf("Double")) - 1);
			
			notifySpectatorsMoveMade(((DoubleMove) hiddenMove).firstMove());

			currentRound++;
			notifySpectatorsRoundStarted();
			
			notifySpectatorsMoveMade(((DoubleMove) hiddenMove).secondMove());
		}
		
        // If we reached the end of the players list
        currentPlayer.next();
        if (! currentPlayer.hasNext())
        {
            currentPlayer = players.listIterator(0);
            currentRotationComplete = true;
            if (isGameOver())
            {
                notifySpectatorsGameOver(getWinningPlayers());
            }
            else
            {
                notifySpectatorsRotationComplete();
            }
        }
        else
        {
            if (isGameOver())
            {
                notifySpectatorsGameOver(getWinningPlayers());
            }
            else
            {
                currentRotationComplete = false;
                startRotate();
            }
        }
	}

	@Override
	public Collection<Spectator> getSpectators() {
		return Collections.unmodifiableCollection(spectators);
	}

	@Override
	public List<Colour> getPlayers() {
		return Collections.unmodifiableList(getPlayersModifiableList());
	}

	@Override
	public Set<Colour> getWinningPlayers() {
		return Collections.unmodifiableSet(new HashSet <Colour> (winningPlayersColours));
	}

	@Override
	public int getPlayerLocation(Colour colour) {
		for (ScotlandYardPlayer player : players)
		{
			if (colour == player.colour())
			{
				if (isMrXColour(colour))
				{
					return mrXLastKnownLocation;	
				}

				return player.location();
			}
		}

		return -1;
	}
	
	@Override
	public int getPlayerTickets(Colour colour, Ticket ticket) {
		for (ScotlandYardPlayer player : players)
		{
			if (colour == player.colour())
			{
				return player.tickets().get(ticket);
			}
		}

		return -1;
	}

	@Override
	public boolean isGameOver() {
		// TODO
 
        if ( (isLocationOccupiedByDetective(mrX.location())) ||
             ((isMrXColour(getCurrentPlayer())) &&
              (0 == getAvailableMoves(mrX).size())) )
        {
            // Detectives win
            winningPlayersColours = getPlayersModifiableList();
            winningPlayersColours.remove(Colour.Black); 
            return true;
        }
        // If the rounds are finished
        else if ( ((currentRound == rounds.size()) &&
                   (currentRotationComplete)) ||
                  (areAllDetectivesStuck()) )
        {
            winningPlayersColours = new LinkedList <Colour> ();
            winningPlayersColours.add(Colour.Black);
            return true;
        }
        
        return false; 
	}

	@Override
	public Colour getCurrentPlayer() {
		Colour currentPlayerColour;

		// Get colour of current player. next() moves the pointer, so we need
		// to get the current player iterator to the previous value
		currentPlayerColour = currentPlayer.next().colour();

		// Restoring currentPlayer
		currentPlayer.previous();

		return currentPlayerColour;
	}

	@Override
	public int getCurrentRound() {
		return currentRound;
	}

	@Override
	public boolean isRevealRound() {
		return (3 == currentRound) ||
			   (8 == currentRound) ||
			   (13 == currentRound) ||
			   (18 == currentRound);
	}

	@Override
	public List<Boolean> getRounds() {
		return Collections.unmodifiableList(rounds);
	}

	@Override
	public ImmutableGraph<Integer, Transport> getGraph() {
		return new ImmutableGraph(graph);
	}


	// Input: player (PlayerConfiguration)
	// Preconditions: - Checks if all the ticket types are present in the tickets map of the player
	// 				  - Checks if the number of tickets of any type is negative
	// 				  - If the player is a detective, it checks if the number of Double and Secret
	// 				    tickets is 0
	// Output: -
	// Postconditions: -
	private void
	checkPlayerTickets(PlayerConfiguration player)
	{
		// Check if all the tickets are present as keys in the player's map of tickets
		for (Ticket ticket : Ticket.values())
		{
			if (! player.tickets.containsKey(ticket))
			{
				throw new IllegalArgumentException("Player does not have a key for all the type of tickets");
			}

			if (0 > player.tickets.get(ticket))
			{
				throw new IllegalArgumentException("Number of tickets is negative");
			}
		}

		// If player is detective, check for Double and Secret Tickets
		if (! isMrX(player))
		{
			if (0 != player.tickets.get(Ticket.Double))
			{
				throw new IllegalArgumentException("Illegal number of Double Tickets for detective");
			}

			if (0 != player.tickets.get(Ticket.Secret))
			{
				throw new IllegalArgumentException("Illegal number of Secret Tickets for detective");
			}
		}
	}

	// Input: player (ScotlandYarPlayer)
	// Preconditions: Player is part of the game
	// Output: availableMoves (Set <Move>)
	// Postconditions : The reurned set of moves contains all the available moves the player can make
	private Set <Move>
	getAvailableMoves(ScotlandYardPlayer player)
	{
		Collection <Edge <Integer, Transport>> 	edgesFromNode;
		HashSet <Move> 							availableMoves = new HashSet <Move> ();
		String[] 								classicTransportNames = {"Bus",
																  		 "Taxi",
																  		 "Underground"};

		edgesFromNode = graph.getEdgesFrom(graph.getNode(player.location()));
		//System.out.format("PLAYER LOCATION: %d\n", player.location());

		// For both mrX and detectives
		for (Edge <Integer, Transport> edge : edgesFromNode)
		{
			// Ignore boat transportation
			if ((Transport.Boat != edge.data()) &&
				(0 < player.tickets().get(Ticket.fromTransport(edge.data()))) &&
				(! isLocationOccupiedByDetective(edge.destination().value())) )
			{
				availableMoves.add(new TicketMove(player.colour(),
												  Ticket.fromTransport(edge.data()),
												  edge.destination().value()));
			}
		}


		// For mrX only
		if (isMrX(player))
		{
			int			tempLocation;
			List <Move> doubleMoves = new LinkedList <Move> ();
			
			// If mrX has any secret move tickets
			if (mrX.tickets().get(Ticket.valueOf("Secret")) > 0)
			{
				List <Move> secretMoves = new LinkedList <Move> ();

		        for (Edge <Integer, Transport> edge : edgesFromNode)
				{
				    if (! isLocationOccupiedByDetective(edge.destination().value()))
				    {
                        secretMoves.add(new TicketMove(player.colour(),
                                                       Ticket.Secret,
                                                       edge.destination().value()));
                    }
				}

				// Add the secret moves in availableMoves
				for (Move move : secretMoves)
				{
					availableMoves.add(move);
				}
			
				// Check for boat; Note that I know I have at least one Secret ticket
				for (Edge <Integer, Transport> edge : edgesFromNode)
				{
					if ( (Transport.Boat == edge.data()) &&
						 (! isLocationOccupiedByDetective(edge.destination().value())) )
					{
						availableMoves.add(new TicketMove(player.colour(),
														  Ticket.Secret,
														  edge.destination().value()));
					}
				}
			}

			// Store the original location of the player
			tempLocation = player.location();
			
			// Add double moves
			// The set of available moves we have built so far contains valid first moves
			// in a double move, so we will use it
			if ( (player.tickets().get(Ticket.Double) > 0) &&
				 (currentRound + 1 < rounds.size()) )
			{
				for (Move move : availableMoves)
				{
					// Temporarily decrement the number of tickets of the type used for the first move
					player.tickets().replace(((TicketMove) move).ticket(),
											 player.tickets().get(((TicketMove) move).ticket()) - 1);
					
					// Temporarily move the player to the location of the first move
					player.location(((TicketMove) move).destination());

					for (Edge <Integer, Transport> edge : 
							graph.getEdgesFrom(graph.getNode((player.location()))))
					{
						// Check if mrX has the ticket required to travel to the second destination.
						// Keep in mind that we should consider the fact that he already used a ticket
						// for the first move, so he may not have one ticket for the second step
						if ( (player.tickets().get(Ticket.fromTransport(edge.data())) > 0) &&
							 (! isLocationOccupiedByDetective(edge.destination().value())) )
						{
							doubleMoves.add(new DoubleMove(player.colour(),
														   (TicketMove) move,
														   new TicketMove(player.colour(),
																		  Ticket.fromTransport(edge.data()),
																		  edge.destination().value())));
						}
						if ( (player.tickets().get(Ticket.valueOf("Secret")) > 0) &&
							 (! isLocationOccupiedByDetective(edge.destination().value())) )
						{
							doubleMoves.add(new DoubleMove(player.colour(),
														   (TicketMove) move,
														   new TicketMove(player.colour(),
																		  Ticket.Secret,
																		  edge.destination().value())));
						}
					}

					// Restore the correct number of tickets of the type used for move
					player.tickets().replace(((TicketMove) move).ticket(),
											 player.tickets().get(((TicketMove) move).ticket()) + 1);
										 
				}
			}
			// Restore the original location of the player
			player.location(tempLocation);
			
			for (Move doubleMove : doubleMoves)
			{
				availableMoves.add(doubleMove);
			}
		}

		if ((! isMrX(player)) &&
		    (availableMoves.isEmpty()))
		{
			availableMoves.add(new PassMove(player.colour()));
		}

		return availableMoves;
	}

	// Given a colour, it returns the player (ScotlandYardPlayer)
	private ScotlandYardPlayer
	getPlayer(Colour colour)
	{
		for (ScotlandYardPlayer player : players)
		{
			if (colour == player.colour())
			{
				return player;
			}
		}

		return null;
	}

	// Checks if a player (ScotlandYardPlayer) is mrX
	private boolean
	isMrX(ScotlandYardPlayer player)
	{
		return Colour.Black == player.colour();
	}

	// Checks if a player (PlayerConfiguration) is mrX
	private boolean
	isMrX(PlayerConfiguration player)
	{
		return Colour.Black == player.colour;
	}

	// Check if a colour (Colour) is mrX's, i.e. Colour.Black
	private boolean
	isMrXColour(Colour colour) {
		return colour == Colour.Black;
	}

	// Input: potentialLocation (int) - the location to which we want to move
	// Preconditions: The ptential location should be a valid location integer
	// Output: Boolean - true if the location is occupied by a detective, 
	// 					 false otherwise
	// Postconditions: -
	private boolean
	isLocationOccupiedByDetective(int potentialLocation)
	{
		// Check if the location is valid
		if (! graph.containsNode(potentialLocation))
		{
			throw new IllegalArgumentException("An invalid location integer passed");
		}

		for (ScotlandYardPlayer player : players)
		{
			if ((! isMrX(player)) &&
			    (player.location() == potentialLocation))
			{
				return true;
			}
		}

		return false;
	}

	// Notifies the spectators on a made move
	private void
	notifySpectatorsMoveMade(Move move)
	{
		for (Spectator spectator : spectators)
		{	
			spectator.onMoveMade(this,
								 move);
		}
	}

	// Notifies the spectators on round started
	private void
	notifySpectatorsRoundStarted()
	{
		for (Spectator spectator : spectators)
		{
			spectator.onRoundStarted(this,
									 currentRound);
		}
	}

	// Notifies the spectators on game over
	private void
	notifySpectatorsGameOver(Set <Colour> winningPlayers)
	{
		for (Spectator spectator : spectators)
		{
			spectator.onGameOver(this,
								 winningPlayers);
		}
	}

	// Notifies the spectators on rotation complete
	private void
	notifySpectatorsRotationComplete()
	{
		for (Spectator spectator : spectators)
		{
			spectator.onRotationComplete(this);
		}
	}

	// Update MrX's last known location if it's reveal round
    private void
    updateMrXLastKnownLocation()
    {
        if (rounds.get(currentRound - 1))
        {
            mrXLastKnownLocation = mrX.location();
        }
    }

    // Returns true if the current round is a reveal round
    private boolean
    currentRoundIsReveal()
    {
        return rounds.get(currentRound - 1);
    }

    // Input : move (Move)
    // Preconditions: The move should be valid, i.e. exists a node in the graph such that I
    //                can apply the move. If the move is a DoubleMove, the second move
    //                must be available from the location arrived after the first move
    // Output : move if reveal round,
    //          a new move, with the location(s) set to 0, if it is not a reveal round
    // Postconditions: the returned move is valid
    private Move
    hideMoveIfNotRevealRound(Move move)
    {
        checkMoveIsValid(move);

        if (move instanceof TicketMove)
        {
            return hideTicketMoveIfNotRevealRound((TicketMove) move);
        }
        else if (move instanceof DoubleMove)
        {
            return hideDoubleMoveIfNotRevealRound((DoubleMove) move);
        }

        return move;
    }

    // Checks if a move is valid
    private void
    checkMoveIsValid(Move move)
    {
        // Every PassMove is valid
        if (move instanceof PassMove)
        {
            return;
        }
        else if (move instanceof TicketMove)
        {
            checkTicketMoveIsValid((TicketMove) move);
        }
        else if (move instanceof DoubleMove)
        {
            checkDoubleMoveIsValid((DoubleMove) move);
        }
    }

    // Checks if a TicketMove is valid
    private void
    checkTicketMoveIsValid(TicketMove move)
    {
        // Check if the location is valid
        if (! graph.containsNode(move.destination()))
        {
            throw new IllegalArgumentException("The provided move contains a location that does not exist");
        }

        // Check if the location is isolated
        if (0 == graph.getEdgesTo(graph.getNode(move.destination())).size())
        {
            throw new IllegalArgumentException("The provided move contains a destination unconnected to any other location");
        }

        // If the used ticket is a secret, then it's a valid move, since we know the destination
        // is connected
        if (Ticket.Secret == move.ticket())
        {
            return;
        }

        // Check if there is an edge that matches the ticket
        for (Edge <Integer, Transport> edge : graph.getEdgesTo(graph.getNode(move.destination())))
        {
            if ( (move.ticket() == Ticket.fromTransport(edge.data())) ||
                 (move.ticket() == Ticket.Secret) )
            {
                // We have found a node that is connected to our destination and whose
                // method of transportation matches the ticket's
                return;
            }
        }

        // If we haven't found a node, throw an error
        throw new IllegalArgumentException("The move's destination is not connected to a node such that the ticket matches the transport");
    }

    // Checks if a DoubleMove is valid
    private void
    checkDoubleMoveIsValid(DoubleMove move)
    {
        // Check if the individual moves are valid
        checkTicketMoveIsValid(move.firstMove());
        checkTicketMoveIsValid(move.secondMove());

        // Check if the first destination is connected to the second one and if the 
        // ticket matches the transportation method
        for (Edge <Integer, Transport> edge : graph.getEdgesFrom(graph.getNode(move.firstMove().destination())))
        {
            // The second move is valid
            if ( (edge.destination().value() == move.secondMove().destination()) &&
                 ( (Ticket.fromTransport(edge.data()) == move.secondMove().ticket()) ) ||
                   (Ticket.Secret == move.secondMove().ticket()) )
            {
                return;
            }
        }

        // If we reach this point, we have to throw an exception
        throw new IllegalArgumentException("The second move can not be performed from the destination of the first move");
    }

    // Hides the location of the move if reveal round, otherwise returns the move
    private Move
    hideTicketMoveIfNotRevealRound(TicketMove move)
    {
        checkTicketMoveIsValid(move);

        if (! rounds.get(currentRound))
        {
            return new TicketMove(move.colour(),
                                  move.ticket(),
                                  mrXLastKnownLocation);
        }
        
        mrXLastKnownLocation = move.destination();
        return move;
    }

    // Hides one of the destinations of the double move if reveal round
    private Move
    hideDoubleMoveIfNotRevealRound(DoubleMove move)
    {
        checkDoubleMoveIsValid(move);

        // If the round corresponding to the first move is reveal round
        if ( (rounds.get(currentRound)) &&
             (! rounds.get(currentRound + 1)) )
        {
            mrXLastKnownLocation = move.firstMove().destination();
            return new DoubleMove(move.colour(),
                                  move.firstMove(),
                                  new TicketMove(move.secondMove().colour(),
                                                 move.secondMove().ticket(),
                                                 mrXLastKnownLocation));
        }
        // If the round corresponding to the second move is reveal round
        else if ( (rounds.get(currentRound + 1)) &&
                  (! rounds.get(currentRound)) )
        {
            TicketMove newFirstMove = new TicketMove(move.firstMove().colour(),
                                                     move.firstMove().ticket(),
                                                     mrXLastKnownLocation);
            mrXLastKnownLocation = move.finalDestination();
            return new DoubleMove(move.colour(),
                                  newFirstMove,
                                  move.secondMove());
        }
        // If both are reveal
        else if ( (rounds.get(currentRound)) &&
                  (rounds.get(currentRound + 1)) )
        {
            mrXLastKnownLocation = move.finalDestination();
            return move;
        }

        // If no reveal round
        return new DoubleMove(move.colour(),
                              new TicketMove(move.firstMove().colour(),
                                             move.firstMove().ticket(),
                                             mrXLastKnownLocation),
                              new TicketMove(move.secondMove().colour(),
                                             move.secondMove().ticket(),
                                             mrXLastKnownLocation));
    }

    // Returns a modifiable list of players' colour
    private LinkedList <Colour>
    getPlayersModifiableList()
    {
        LinkedList <Colour> playerColours = new LinkedList <Colour> ();

        for (ScotlandYardPlayer player : players)
        {
            playerColours.add(player.colour());    
        }

        return playerColours;
    }

    // Returns true if all detectives are stuck
    private boolean
    areAllDetectivesStuck()
    {
        HashSet <Move> moves;
        int            passMoveDet;

        passMoveDet = 0;
        for (ScotlandYardPlayer player : players)
        {
            if (! isMrX(player))
            {
                moves = (HashSet <Move>) getAvailableMoves(player);
                if ( (1 == moves.size()) &&
                     (moves.iterator().next() instanceof PassMove) )
                {
                    passMoveDet++;
                }
            }
        }

        return passMoveDet == players.size() - 1;
    }
}
