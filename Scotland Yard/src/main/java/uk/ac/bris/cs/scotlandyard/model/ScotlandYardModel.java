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

// TODO implement all methods and pass all tests
public class ScotlandYardModel implements ScotlandYardGame, Consumer <Move> {

	private List <Boolean> 						rounds;
	private Graph <Integer, Transport> 			graph;
	private ScotlandYardPlayer					mrX;
	private LinkedList <Spectator>				spectators;
	private ListIterator <ScotlandYardPlayer>	currentPlayer;
	private LinkedList <ScotlandYardPlayer>		players = new LinkedList <ScotlandYardPlayer> ();
	private int									currentRound;
	private int									mrXLastKnownLocation;

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
		
		// Add MrX to the players list										  
		players.add(this.mrX);	

		// Check mrX tickets
		checkPlayerTickets(mrX);

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

			if (locations.contains(currentDetective.location))
			{
				throw new IllegalArgumentException("Duplicate location");	
			}

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

		currentRound = 1;
		mrXLastKnownLocation = 0;
		
	}

	@Override
	public void registerSpectator(Spectator spectator) {
		if (spectators.contains(spectator))
		{
			throw new IllegalArgumentException("Spectator already exists");
		}
		
		spectators.add(spectator);
	}

	@Override
	public void unregisterSpectator(Spectator spectator) {
		if (! spectators.remove(spectator))
		{
			throw new IllegalArgumentException("Spectator does not exist");
		}
	}

	@Override
	public void startRotate() {
		// TODO
		ScotlandYardPlayer playerToMove;


		playerToMove = getPlayer(getCurrentPlayer());
		playerToMove.player().makeMove(this,
							  		   playerToMove.location(),
									   getAvailableMoves(playerToMove),
									   //new HashSet <Move> (),
							  		   this);
	}

	@Override
	public void
	accept(Move move)
	{
		HashSet <Move> 		availableMoves;
		ScotlandYardPlayer 	player;

		if (null == move)
		{
			throw new NullPointerException();
		}

		availableMoves = (HashSet <Move>) getAvailableMoves(getPlayer(getCurrentPlayer()));
		if (! availableMoves.contains(move))
		{
			throw new IllegalArgumentException();
		}

		// Let spectators know about the made move
		notifySpectatorsMoveMade(move);

		// Notify spectators about started round if mrX moves
		notifySpectatorsRoundStarted();

		player = getPlayer(getCurrentPlayer());
		// Update the player's location and tickets
		if (move instanceof TicketMove)
		{
			player.location(((TicketMove) move).destination());
			player.tickets().replace(((TicketMove) move).ticket(),
									 player.tickets().get(((TicketMove) move).ticket()) - 1);
			mrX.tickets().replace(((TicketMove) move).ticket(),
								  player.tickets().get(((TicketMove) move).ticket()) + 1);
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
			currentRound++;
			notifySpectatorsMoveMade(((DoubleMove) move).firstMove());
			notifySpectatorsMoveMade(((DoubleMove) move).secondMove());
			notifySpectatorsRoundStarted();
		}


		if (currentPlayer == players.getLast())
		{
			currentPlayer = players.listIterator(0);
			currentRound++;
			notifySpectatorsRotationComplete();
		}

		if (rounds.get(currentRound))
		{
			mrXLastKnownLocation = mrX.location();
		}
		startRotate();
	}

	@Override
	public Collection<Spectator> getSpectators() {
		return Collections.unmodifiableCollection(spectators);
	}

	@Override
	public List<Colour> getPlayers() {
		List playersColourList = new LinkedList <Colour> ();

		for (ScotlandYardPlayer player : players)
		{
			playersColourList.add(player.colour());
		}
		
		return Collections.unmodifiableList(playersColourList);
	}

	@Override
	public Set<Colour> getWinningPlayers() {
		// TODO
		//throw new RuntimeException("Implement me");
		return Collections.unmodifiableSet(new HashSet <Colour> ());
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
		//throw new RuntimeException("Implement me");
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
		Set <Move> 								availableMoves = new HashSet <Move> ();
		String[] 								classicTransportNames = {"Bus",
																  		 "Taxi",
																  		 "Underground"};

		edgesFromNode = graph.getEdgesFrom(graph.getNode(player.location()));

		// For both mrX and detectives
		for (Edge <Integer, Transport> edge : edgesFromNode)
		{
			if (edge.data() == Transport.Taxi)
			System.out.format(" %d", edge.destination().value());

			// Ignore boat transportation
			if ((Transport.Boat != edge.data()) &&
				(0 < player.tickets().get(Ticket.fromTransport(edge.data()))) &&
				(! isLocationOccupied(edge.destination().value())) )
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
			
			// If mrX has any secret move tickets, add a move for every already added move, using a secret ticket
			// instead, and check for moves using a boat
			if (mrX.tickets().get(Ticket.valueOf("Secret")) > 0)
			{
				List <Move> secretMoves = new LinkedList <Move> ();

				for (Move move : availableMoves)
				{
					secretMoves.add(new TicketMove(player.colour(),
												   Ticket.Secret,
												   ((TicketMove) move).destination()));
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
						 (! isLocationOccupied(edge.destination().value())) )
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
				 (currentRound < rounds.size()) )
			{
				for (Move move : availableMoves)
				{
					// Temporarily decrement the number of tickets of the type used for the first move
					player.tickets().replace(((TicketMove) move).ticket(),
											 player.tickets().get(((TicketMove) move).ticket()) - 1);
					
					// Temporarily move the player to the location of the first move
					player.location(((TicketMove) move).destination());

					for (Edge <Integer, Transport> edge : 
							graph.getEdgesFrom(graph.getNode((((TicketMove) move).destination()))))
					{
						// Check if mrX has the ticket required to travel to the second destination.
						// Keep in mind that we should consider the fact that he already used a ticket
						// for the first move, so he may not have one ticket for the second step
						
						if ( (player.tickets().get(Ticket.fromTransport(edge.data())) > 0) &&
							 (! isLocationOccupied(edge.destination().value())) )
						{
							doubleMoves.add(new DoubleMove(player.colour(),
														   (TicketMove) move,
														   new TicketMove(player.colour(),
																		  Ticket.fromTransport(edge.data()),
																		  edge.destination().value())));
						}
						if ( (player.tickets().get(Ticket.valueOf("Secret")) > 0) &&
							 (! isLocationOccupied(edge.destination().value())) )
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

		if (availableMoves.isEmpty())
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
	// Output: Boolean - true if the location is occupied, 
	// 					 false otherwise
	// Postconditions: -
	boolean
	isLocationOccupied(int potentialLocation)
	{
		// Check if the location is valid
		if (! graph.containsNode(potentialLocation))
		{
			throw new IllegalArgumentException("An invalid location integer passed");
		}

		for (ScotlandYardPlayer player : players)
		{
			if (player.location() == potentialLocation)
			{
				return true;
			}
		}

		return false;
	}

	// Notifies the spectators on a made move
	void
	notifySpectatorsMoveMade(Move move)
	{
		for (Spectator spectator : spectators)
		{	
			spectator.onMoveMade(this,
								 move);
		}
	}

	// Notifies the spectators on round started
	void
	notifySpectatorsRoundStarted()
	{
		for (Spectator spectator : spectators)
		{
			spectator.onRoundStarted(this,
									 currentRound);
		}
	}

	// Notifies the spectators on game over
	void
	notifySpectatorsGameOver(Set <Colour> winningPlayers)
	{
		for (Spectator spectator : spectators)
		{
			spectator.onGameOver(this,
								 winningPlayers);
		}
	}

	// Notifies the spectators on rotation complete
	void
	notifySpectatorsRotationComplete()
	{
		for (Spectator spectator : spectators)
		{
			spectator.onRotationComplete(this);
		}
	}
}
