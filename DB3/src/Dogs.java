/*
 * @title DB3 - AE2
 * @author Irina Preda
 * @author 1102452p
 *
 * time spent: approx 15h.
 *
 */

import javax.swing.*;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.sql.*;

public class Dogs extends JFrame {
   //swing components
   private JButton motherButton = new JButton(), 
                   fatherButton = new JButton(), 
                   originalButton = new JButton("Original dog"), 
                   restartButton = new JButton("Choose another dog"),
                   quitButton = new JButton("Quit");
   private JLabel dogInfo = new JLabel(), 
                  pedInfo = new JLabel();
   private Stack<String> dogStack = new Stack<String>();
   private String topMother, topFather; //the mother and father names of the dog
                                        //at the top of the dogStack
   //database stuff  
   private Vector<String> temp;
   private static final String username = "lev3_13_1102452p";
   private static final String password = "test";
   private static final String connStr = "jdbc:postgresql://yacata.dcs.gla.ac.uk:5432/lev3_13_1102452p"; 
   private static Connection conn;
   private static PreparedStatement dogInfoStmt, dogsOwnedStmt, showsStmt, eligibleStmt, disqualifyStmt, 
   									findDisqPosStmt, findDogsStmt, findDogPosStmt, updateStmt, 
   									siblingStmt, childStmt, grandchildStmt, parentStmt, breedStmt, 
                                    parentBreedStmt;


   //output a supplied error message, the details of the exception, and then die
   private static void doError(Exception e, String msg)
   {
      System.out.println(msg);
      e.printStackTrace();
      System.exit(1);
   }
   
   //output any warnings associated with <conn> or <stmt>
   private static void printWarnings(Connection conn, Statement stmt)
   {
      try {
         SQLWarning currConnWarn = conn.getWarnings();
         while (currConnWarn != null)
            System.out.println("Warning: " + currConnWarn.getMessage());
         SQLWarning currStmtWarn = stmt.getWarnings();
         while (currStmtWarn != null)
            System.out.println("Warning: " + currStmtWarn.getMessage());
      } catch(Exception e) {
         doError(e, "Problem getting warnings");
      }
   } 

   static void init() {
      //database stuff - load driver, and then obtain connection
      try {
    	  Class.forName("org.postgresql.Driver");
      } catch(Exception e) {
    	  doError(e, "Failed to load oracle driver");
      }
      try {
    	  conn = DriverManager.getConnection(connStr,username, password);
      } catch(Exception e) {
         doError(e, "Failure to obtain connection: " + connStr);
      }
   }

   private Dogs() {
	   
	   // sql query to show all dogs of the owner
	   
	   String dogsOwnedQuery = "SELECT Dog.name "
				+ "FROM Dog, (SELECT Dog.ownerid FROM Dog WHERE Dog.name=?) AS dogsOwn "
				+ "WHERE dogsOwn.ownerid = Dog.ownerid";
	   
	   try{
		   dogsOwnedStmt = conn.prepareStatement(dogsOwnedQuery);
	   }catch(SQLException e){
		   doError(e,"Dogs owned info failed to complete" + dogsOwnedStmt); 
	   }	   
	   
	   // query to show attendance
	   
		String showsQuery = "SELECT Attendance.showname, Attendance.opendate "
						+ "FROM Attendance, Dog "
						+ "WHERE Dog.dogid = Attendance.dogid AND Dog.name=?";
		try {
			showsStmt = conn.prepareStatement(showsQuery);
		} catch (SQLException e) {
			doError(e, "Attendance info failed to complete" + showsStmt);
		}
	   
		// sql query to show eligibility 

		String eligibleQuery = "SELECT COUNT(*)"
				+ " FROM Attendance"
				+ " WHERE Attendance.dogid IN  ("
				+ "								SELECT Dog.dogid FROM Dog"
				+ "								WHERE Dog.name=?"
				+ "							  )"
				+ " HAVING COUNT(*) = (	"
				+ "						SELECT COUNT (k)"
				+ "                		FROM ("
				+ "								SELECT DISTINCT Show.showname, Show.opendate"
				+ " 							FROM Show"
				+ "							 ) as k"
				+ "					 )";
		try {
			eligibleStmt = conn.prepareStatement(eligibleQuery);
		} catch (SQLException e) {
			doError(e, "Eligible info failed to complete" + eligibleStmt);
		}
		
      String ownerCount = "SELECT Owner.ownerid, COUNT(Owner.ownerid) as noOfDogs FROM Owner LEFT JOIN Dog ON Dog.ownerid = Owner.ownerid GROUP BY Owner.ownerid";   
    
      //finds all the data that are single values per dog
      String dogInfoQuery = 
                   "SELECT Dog.breedname, Dog.mothername, " +
                   "       Dog.fathername, Kennel.kennelname, " +
                   "       Kennel.address, Owner.name, " +
                   "       OwnerCount.noOfDogs " +
                   "FROM Dog, Kennel, " +
                   "     Owner, (" + ownerCount + ") OwnerCount " +
                   "WHERE Dog.kennelname=Kennel.kennelname AND " +
                   "      Dog.ownerid=Owner.ownerid AND " +
                   "      Owner.ownerid=OwnerCount.ownerid AND " +
                   "      Dog.name=?"; //parameterised by dog name
      try {
         dogInfoStmt = conn.prepareStatement(dogInfoQuery);
      } catch(SQLException e) {
         doError(e, "Dog info statement failed to compile: " + dogInfoQuery);
      }
      //finds the dog's siblings (incl. half siblings), but not the dog itself
      //parameterised by (1) the dog's mothername, (2) the dog's fathername and (3) the dog's 
      //own name
      //Result: name
      String siblingQuery = "SELECT Dog.name FROM Dog WHERE (Dog.mothername=? OR Dog.fathername=?) AND Dog.name<>?";
    		 
      try {
         siblingStmt = conn.prepareStatement(siblingQuery);
      } catch(SQLException e) {
         doError(e, "Sibling statement failed to compile: " + siblingStmt);
      }
      //finds the dog's children. We don't know if it's a mother or a father, so
      //we should match either (although presumably only one for any given dog!)
      //the query should be parametrised by (1) the dog's mothername, and (2) the dog's fathername
      //Result: name
      //Sorted by: name
      String childQuery = "SELECT Dog.name FROM Dog WHERE Dog.mothername=? OR Dog.fathername=?";
    		  
      try {
         childStmt = conn.prepareStatement(childQuery);
      } catch(SQLException e) {
         doError(e, "Child query failed to compile: " + childQuery);
      }
      //finds the dog's grandchildren, again matching father or mother as above
      //you will need two copies of the relation Dog: D1 and D2 
      //in this query the current dog "?" should be D1's mother or father, and D2 should be the 
      //grandchild 
      //Result: D2.name
      String grandchildQuery = "SELECT D2.name FROM Dog as D2, (SELECT Dog.name FROM Dog WHERE Dog.mothername=? OR Dog.fathername=?) as D1 WHERE D2.mothername = D1.name OR D2.fathername = D1.name";
    		 
      try {
         grandchildStmt = conn.prepareStatement(grandchildQuery);
      } catch(SQLException e) {
         doError(e, "Granchild query failed to compile: " + grandchildQuery);
      }
      //finds parents of two dogs at once, the parameters are the two dogs
     
      String parentQuery = "SELECT mothername, fathername " + 
                           "FROM Dog " + 
                           "WHERE name=? OR name=? ";
      try {
         parentStmt = conn.prepareStatement(parentQuery);
      } catch(SQLException e) {
         doError(e, "Failed to compile grandparent statement: " + parentQuery);
      }
      //a query to return all known breeds for the supplied dog, used in
      //getParentsBreeds()
  
      String parentBreedQuery = "SELECT Parent.breedname " +
                                "FROM Dog Curr, Dog Parent " +
                                "WHERE (Curr.mothername=Parent.name OR " +
                                "       Curr.fathername=Parent.name) " +
                                "  AND Curr.name=?";
      try {
         parentBreedStmt = conn.prepareStatement(parentBreedQuery);
      } catch(SQLException e) {
         doError(e, "Failed to compile parent breed stmt: " + parentBreedStmt);
      }

      //info text boxes and labels
      JPanel dogInfoPanel = new JPanel();
      dogInfoPanel.add(dogInfo);
      dogInfoPanel.setBorder(
         BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Dog information"),
            BorderFactory.createEmptyBorder(10,10,10,10)));
      JPanel pedInfoPanel = new JPanel();
      pedInfoPanel.add(pedInfo);
      pedInfoPanel.setBorder(
         BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Pedigree"),
            BorderFactory.createEmptyBorder(10,10,10,10)));
      //positioning of components
      Container cp = getContentPane();
      cp.setLayout(new BoxLayout(cp,BoxLayout.X_AXIS));
      Box buttonBox = Box.createVerticalBox();
      buttonBox.add(motherButton);
      buttonBox.add(fatherButton);
      buttonBox.add(originalButton);
      buttonBox.add(Box.createVerticalGlue());
      buttonBox.add(restartButton);
      buttonBox.add(quitButton);
      Box infoBox = Box.createVerticalBox();
      infoBox.add(dogInfoPanel);
      infoBox.add(pedInfoPanel);
      cp.add(infoBox);
      cp.add(buttonBox);
      //button handling
      ButtonHandler bh = new ButtonHandler();
      motherButton.addActionListener(bh);
      fatherButton.addActionListener(bh);
      originalButton.addActionListener(bh);
      restartButton.addActionListener(bh);
      quitButton.addActionListener(bh);
      //set up the dog to be displayed
      dogStack.push(getDogChoice());
      redisplay(); //display dog info
      originalButton.setEnabled(false); //cannot go to previous dog
      //window settings
      Dimension screen = getToolkit().getScreenSize();
      setBounds(0, 0, 700, 700); //position (0,0) and size 700x700
      setDefaultCloseOperation(EXIT_ON_CLOSE);
      setTitle("Dog information");
      setVisible(true);
   }



   //Returns the most accurate possible breed information for the supplied dog's
   //parents. For a parent that is not in the database we will assume that the
   //breed is the same as the supplied dog's, unless the supplied dog is not in
   //the database in which case we assume <assumeBreed>.
   private Vector<String> getParentBreeds(String dogname, String assumeBreed)
   {
      Vector<String> retval = new Vector<String>(); //breeds we've found
      //get as many parent breeds as possible
      try {
         parentBreedStmt.setString(1, dogname);
         ResultSet results = parentBreedStmt.executeQuery();
         printWarnings(conn, parentBreedStmt);
         //Get all the breed information available from the database.
         //Due to key constraints there are at most 2 rows in the result.
         while (results.next())
            retval.add(results.getString(1));
      } catch(SQLException e) {
         doError(e, "Parent breed query failed to execute");
      }
      //if we have all the possible parents, then we can stop now, otherwise use
      //best guesses as per the comment above
      if (retval.size() == 2)
         return retval;
      else {
         try {
            dogInfoStmt.setString(1, dogname);
            ResultSet results = dogInfoStmt.executeQuery();
            printWarnings(conn, dogInfoStmt);
            if (results.next()) //try to use the supplied dog's real breed
               while (retval.size() < 2)
                  retval.add(results.getString(1));
            else //if real breed is not available use the assumed breed
               while (retval.size() < 2)
                  retval.add(assumeBreed);
         } catch(SQLException e) {
            doError(e, "Failed to execute breed query");
         }
      }
      return retval;
   }

   //displays the dog data present on the top of the stack of names
   private void redisplay() {
      if (dogStack.empty()) {
         System.out.println("Unexpectedly exhausted dogs!");
         System.exit(1);
      }
      String dogName = dogStack.peek();
      ResultSet results;
      //per-dog information
      String name = null, breed = null, mother = null, father = null, 
             kennel = null, kennAddr = null, owner = null;
      int ownerDogs = 0;
      //information with arbitrary numbers of values per dog
      Vector<String> siblings = new Vector<String>(), 
                     children = new Vector<String>(), 
                     grandchildren = new Vector<String>(),
                     grandparents = new Vector<String>();
      try {
         dogInfoStmt.setString(1, dogName);
         results = dogInfoStmt.executeQuery();
         printWarnings(conn, dogInfoStmt);
         //make sure that the dog we are trying to display is in the DB
         if (!(results.next())) {
            //remove it and display a warning message
            JOptionPane.showMessageDialog(this,
               "Information for " + dogStack.pop() + " missing in database.");
            if (dogStack.size() == 1) //in case there is no stack to ascend
               originalButton.setEnabled(false);
            return; //stop now
         }
         //since the data is in the database we continue as normal
         breed = results.getString(1); 
         topMother = mother = results.getString(2);
         topFather = father = results.getString(3);
         kennel = results.getString(4);
         kennAddr = results.getString(5);
         owner = results.getString(6);
         ownerDogs = results.getInt(7);
      } catch(SQLException e) {
         doError(e, "Failed to execute dog info query for " + dogName);
      }
      try {
         //looking for dogs with the same parents
         siblingStmt.setString(1, mother); 
         siblingStmt.setString(2, father);
         siblingStmt.setString(3, dogName); //don't match the dog itself
         results = siblingStmt.executeQuery();
         printWarnings(conn, siblingStmt);
         while (results.next()) //store each sibling name for later display
            siblings.add(results.getString(1));
      } catch(SQLException e) {
         doError(e, "Failed to execute sibling query for " + dogName);
      }
      try {
         //looking for child dogs
         childStmt.setString(1, dogName); childStmt.setString(2, dogName);
         results = childStmt.executeQuery();
         printWarnings(conn, childStmt);
         while (results.next()) //store each name for display later on
            children.add(results.getString(1));
      } catch(SQLException e) {
         doError(e, "Failed to execute child query for " + dogName);
      }
      try {
         //looking for grandchildren
         grandchildStmt.setString(1, dogName); 
         grandchildStmt.setString(2, dogName);
         results = grandchildStmt.executeQuery();
         printWarnings(conn, grandchildStmt);
         while (results.next())
            grandchildren.add(results.getString(1));
      } catch(SQLException e) {
         doError(e, "Failed to execute grandchild query for " + dogName);
      }
      try {
         parentStmt.setString(1, mother); //maternal grandparents
         parentStmt.setString(2, father); //and paternal grandparents
         results = parentStmt.executeQuery();
         printWarnings(conn, parentStmt);
         while (results.next()) { //zero, one or two rows as a result
            if (results.getString(1) != null)
               grandparents.add(results.getString(1)); //grandmother
            if (results.getString(2) != null)
               grandparents.add(results.getString(2)); //grandfather
         }
      } catch(SQLException e) {
         doError(e, "Failed to execute grandparent query for " + dogName);
      }


      Vector <String> ancestors = getAncestors(dogName,null);
      Vector <String> des = getDescendents(dogName,null);


      if (kennAddr == null) kennAddr = "Unknown";

		Vector<String> dogsOwned = getDogsOwned(dogName, null);
		String dogsStr = "";
		int count = 0;
		Collections.sort(dogsOwned);
		for (String s : dogsOwned) {
			count++;
			dogsStr += s;
			dogsStr += " ";
			if (count % 6 == 0)
				dogsStr += "<br>";
		}

		String[] shows = getShows(dogName);
		String showStr;
		if(shows[1] == null) showStr = dogName + " has not attended any show";
		else{
				showStr = "The last show " + dogName + " has attended was " + shows[1] + " on " + shows[0];
		}
		
		boolean eligible = getEligibility(dogName);
		String eligibleStr;
		if(eligible)
			eligibleStr = "Eligible for Service Award";
		else
			eligibleStr = "Not Eligible for Service Award";
      
      //output dog info data
      dogInfo.setText("<html>Name: " + dogName + "<br>" +
                      "Breed: " + breed + "<br>" +
                      "Kennel: " + kennel + " " + eligibleStr + "<br>" +
                      "Address: " + kennAddr + "<br>" +
                      dogName + "'s owner: " + 
                      owner + " (owns " + ownerDogs + " dogs)" + "<br>" + dogsStr + "<br>" + showStr + " "  + "</html>");
      //put together a message about how many parents are not named in the DB
      String parentMissingInfo = ""; //nothing by default 
      if (father == null && mother == null)
         parentMissingInfo = "(2 missing)";
      else if (father == null ^ mother == null) //exclusive OR
         parentMissingInfo = "(1 missing)";
      //message about how many grandparents are missing, assume dog isn't inbred
      String grandparentMissingInfo = "";
      if (grandparents.size() != 4)
         grandparentMissingInfo = "(" + (4 - grandparents.size()) + " missing)";
      //put together the required breed information



      Vector<String> breeds = getParentBreeds(mother, breed);
      breeds.addAll(getParentBreeds(father, breed));
      breeds.add("ZZZZZZZZZZZZZZZZZZZZZZZZ"); //sentinel to help the output code


      Object[] breedStrings = breeds.toArray();
      Arrays.sort(breedStrings);
    //  boolean PureBred = breedStrings[0] == breedStrings[1] &&
     //                      breedStrings[1] == breedStrings[2] &&
      //                     breedStrings[2] == breedStrings[3];
      String breedingString = "";
      boolean isPureBred = false;
      for (int i = 0; i < 4; i++) 
         for (int j = i; j < 5; j++) 
            if (!(breedStrings[i].equals(breedStrings[j]))) {
               if(j-i == 4) isPureBred = true;
               breedingString += "(" + (j - i) + "/4 " + breedStrings[i] + ") ";
               i = j - 1; //start again at the start of the next run of breeds
               break;
            }
      //output pedigree info
		String ancSt = "";
		count = 0;
		Collections.sort(ancestors);
		for (String s : ancestors) {
			count++;
			ancSt += s;
			ancSt += " ";
			if (count % 6 == 0)
				ancSt += "<br>";
		}
		
		String desSt = "";
		count = 0;
		Collections.sort(des);
		for (String s : des) {
			count++;
			desSt += s;
			desSt += " ";
			if (count % 6 == 0)
				desSt += "<br>";
		}
		
		
      pedInfo.setText("<html>" + dogName + "'s siblings: " + siblings + "<br>" +
                     dogName + "'s Ancestors: " + ancSt + "<br>" +
                     dogName + "'s Descendents: " + desSt + "<br><br>" +
                      (isPureBred ? "Purebred<br>" : "Not purebred<br>") +
                      "Breeding: " + breedingString +
                      "</html>");
      if (mother != null)
         motherButton.setText(mother + "'s details");
      else {
         motherButton.setText("Not available");
         motherButton.setEnabled(false);
      }
      if (father != null)
         fatherButton.setText(father + "'s details");
      else {
         fatherButton.setText("Not available");
         fatherButton.setEnabled(false);
      }
   }

   //gets a dog name that the user chooses somehow
   private String getDogChoice() {
      try {
         Statement dogStmt = conn.createStatement();
         ResultSet dogRes = dogStmt.executeQuery("SELECT name " + 
                                                 "FROM Dog " + 
                                                 "ORDER BY name");
         Vector<String> dogNames = new Vector<String>();
         while (dogRes.next())
            dogNames.add(dogRes.getString(1));
         if (dogNames.size() == 0) {
            System.out.println("No dogs present in database, exiting.");
            System.exit(1);
         }
         String choice;
         do {
            //use a modal JOptionPane dialog to let the user choose between dogs
            choice = (String)(JOptionPane.showInputDialog(this, //parent dialog
                                            "Select a dog to view", //message
                                            "Select a dog", //title of dialog
                                            JOptionPane.PLAIN_MESSAGE, //urgency
                                            null,
                                            dogNames.toArray(), //choices
                                            dogNames.get(0))); //default choice
         } while (choice == null); //keep going until the user picks one
         return choice;
      } catch(SQLException e) {
         doError(e, "Failed to get dog name choices from DB");
      }
      return null; //unreachable
   }

   //handler to do the work of all the buttons, namely the parent buttons, the
   //back button and the exit button
   private class ButtonHandler implements ActionListener {
      public void actionPerformed(ActionEvent ae) {
         Object src = ae.getSource(); //the button that causes the event
         if (src == motherButton) {
            dogStack.push(topMother); //put mother at the top of the stack
            originalButton.setEnabled(true);
            motherButton.setEnabled(true);
            fatherButton.setEnabled(true);
            redisplay(); //refresh the details on screen to show the mother
         } else if (src == fatherButton) {
            dogStack.push(topFather);
            originalButton.setEnabled(true);
            motherButton.setEnabled(true);
            fatherButton.setEnabled(true);
            redisplay();
         } else if (src == originalButton) {
            dogStack.pop(); //discard currently displayed dog
            if (dogStack.size() == 1)
               originalButton.setEnabled(false); //can't go any further back
            motherButton.setEnabled(true);
            fatherButton.setEnabled(true);
            redisplay();
         } else if (src == restartButton) {
            dogStack.clear();
            dogStack.push(getDogChoice());
            redisplay();
         } else if (src == quitButton) {
            try {
               conn.close();
            } catch(SQLException e) {
               System.out.println("Cannot close DB connection");
               e.printStackTrace();
            } finally {
               System.exit(0);
            }
         }
      }
   }

   public static void main(String[] args) throws SQLException {
      init();
      if (args.length == 3)
      {
          //Example usage: java Dogs 10 'Guide blinds show' '10-06-2003'
          int dogid = Integer.parseInt(args[0]);
          String showname = args[1];
          String showdate = args[2];
          disqualifyDogFromShow(dogid, showname, showdate);
      }
      else
      {
          Dogs window = new Dogs();
      }
   }

   private Vector<String> getDogsOwned(String dogname, Vector<String> dogs) {
	if (dogs == null) dogs = new Vector<String>();

	try {
		dogsOwnedStmt.setString(1,dogname);
		ResultSet results = dogsOwnedStmt.executeQuery();     
       
       while(results.next()) {
    	   dogs.add(results.getString(1));
       }
	}
	catch(Exception e) {
           doError(e, "Failed to execute dogs owned query");
	}
	return dogs;
	
   }
   
	private String[] getShows(String dogname) {
		HashMap<String, String> shows = new HashMap<String, String>();
		int tempday = 0;
		int tempmonth = 0;
		int tempyear = 0;
		String max = "";

		try {
			showsStmt.setString(1, dogname);
			ResultSet results = showsStmt.executeQuery();

			while (results.next()) {
				int day, month, year = 0;
				String showStr = results.getString(1);
				String dateStr = results.getString(2);
				shows.put(dateStr, showStr);

				String[] dateStrArray = dateStr.split("-");
				day = Integer.parseInt(dateStrArray[0]);
				month = Integer.parseInt(dateStrArray[1]);
				year = Integer.parseInt(dateStrArray[2]);

				if ((year > tempyear)
						|| (year == tempyear && month > tempmonth)
						|| (day == tempday && day > tempday)) {
					max = dateStr;
					tempyear = year;
					tempmonth = month;
					tempday = day;
				}
			}	
			
			results.close();
			return  new String[] {max, shows.get(max)}; 

		} catch (Exception e) {
			doError(e, "Failed to execute getShows owned query");
		}
		return null;

	}
     
	private boolean getEligibility(String dogname){

		try {
			eligibleStmt.setString(1, dogname);
			ResultSet results = eligibleStmt.executeQuery();
			if (results.next()) {
				String r = results.getString(1);
				if(r!=null)
					return true;
			}
			
		} catch (Exception e) {
			doError(e, "Failed to execute eligibility query");
		}
	
		return false;
		
	}
      
    private static void disqualifyDogFromShow(int dogid, String showname, String showdate) throws SQLException   {
    	String disqPosition = "";
    	String position = "";
    	try {
    		conn.setAutoCommit(false) ;    		
    		
    		// find the disqualified dog's position in the competition
    		String findDisqPosQuery = "SELECT place FROM Attendance WHERE dogid = " + dogid + " AND showname = '" + showname + "' AND opendate = '" + showdate + "'";
			findDisqPosStmt = conn.prepareStatement(findDisqPosQuery);
    		ResultSet results1 = findDisqPosStmt.executeQuery();
			if (results1.next())	disqPosition  = results1.getString(1);
			
			// delete disqualified dog
			String disqualifyQuery = "DELETE FROM Attendance WHERE dogid = " + dogid + " AND showname = '" + showname + "' AND opendate = '" + showdate + "'";
			disqualifyStmt = conn.prepareStatement(disqualifyQuery);
			disqualifyStmt.executeUpdate();
			
			// find the other dogs in the competition
			if(disqPosition != null && !disqPosition.isEmpty()){
				String findDogsQuery = "SELECT dogid FROM Attendance WHERE dogid != " + dogid + " AND showname = '" + showname + "' AND opendate = '" + showdate + "'";
				findDogsStmt = conn.prepareStatement(findDogsQuery);
				ResultSet results2 = findDogsStmt.executeQuery();
				while (results2.next())	{
					int dogId  = Integer.parseInt(results2.getString(1));
					
		    		String findDogPosQuery = "SELECT place FROM Attendance WHERE dogid = " + dogId + " AND showname = '" + showname + "' AND opendate = '" + showdate + "'";
		    		findDogPosStmt = conn.prepareStatement(findDogPosQuery);
					ResultSet results3 = findDogPosStmt.executeQuery();
					if (results3.next())
						position  = results3.getString(1);
						
					// update the other dogs and their positions in the competition
					String updateRank = "UPDATE Attendance SET place = place - 1 WHERE dogid =" + dogId + " AND showname = '" + showname + "' AND opendate = '" + showdate + "' AND place > " + disqPosition + " AND place IS NOT NULL";
					if(updateRank!=null){
						updateStmt = conn.prepareStatement(updateRank);
						updateStmt.executeUpdate();
					}
				}
			}
    		conn.commit();
    		conn.setAutoCommit(true) ;
    		}
    	catch(SQLException ex) {
    		System.err.println("SQLException: " + ex.getMessage()) ;
    		conn.rollback() ;
			conn.setAutoCommit(true) ;
		}
    }

    private Vector<String> getAncestors(String dogname, Vector<String> anc) {
	if (anc == null) anc = new Vector<String>();

	try {
		parentStmt.setString(1,dogname);
		parentStmt.setString(2,dogname);
		ResultSet results = parentStmt.executeQuery();
      
        
        if(results.next()) {
        	String p1 = results.getString(1);
        	String p2 = results.getString(2);
        	if(p1 != null && !anc.contains(p1)){
        		  anc.add(p1);
        		  getAncestors(p1, anc);
        	}
        	if(p2 != null && !anc.contains(p2)){
      		  anc.add(p2);
      		  getAncestors(p2, anc);
        	
      	}
        }

	}
	catch(Exception e) {
            doError(e, "Failed to execute ancestor query in getBreeding");
	}
	return anc;
	
    }

    private Vector<String> getDescendents(String dogname, Vector<String> desc) {
	
    	if (desc == null) {desc = new Vector<String>();
    						temp = new Vector<String>();
	}
	try {
		childStmt.setString(1,dogname);
		childStmt.setString(2,dogname);
		ResultSet results = childStmt.executeQuery();
      
        while(results.next()) {
        	String kid = results.getString(1);
        	if(kid != null && !desc.contains(kid)){
        		  desc.add(kid);
        		  temp.add(kid);
        		 
        	}
        }
       while(!temp.isEmpty()){
    	   String k = temp.remove(0);
    	   if (k != null)
    		   getDescendents(k,desc);
       }
	}
	catch(Exception e) {
            doError(e, "Failed to execute ancestor query in getBreeding");
	}
	return desc;
	
    }

}

