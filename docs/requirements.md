This document defines requirements for the vertice-api repository, considering some product definitions.

The goal of the product is for personal trainers to create and manage training plans for their costumers/clients.
Also the clients should be able to access the app later on, access their trainings for the day, see which excercises, and start and mark as finished a workout for that given day of the week.

## For the personal trainer part, they shoud be able to:
- Create new excercises if they don't find the one they want
- Add a description for the exercise
- Set how many sets for that execercise
- Manage how the set strategy will be
- Set resting time between sets
- Create a workut that is a training for a given weekday
- Assign the created workout into a training plan for a client/costumer
- A client/costumer can have N training plans assigned to them, up to the personal trainer to decide.
- Whenever a personal trainer need to change/create a new workout for the client, they should be able to use a previous created one as base and just edit it changing what they want, because when a training plan changes, it doesn't mean it changes completely. 


## For the client part, they should be able to:
- Access their training plans on the app whenever they want
- They cannot change enything on the training plan, only the personal trainer can do it
- They should be able to mark a workout as completed
- They should be able to provide feedback about a workout every time they finish one
- The feedback need to be delivered to the personal trainer linking the workout, training plan and client that gave the feedback
- When doing the workout, they need to be able to record the weight they used for every set on every excercise, and when accessing the same workout on the next time, it should be there for them to remember.
- The excercise should also render a video in the ap for the client to see how to execute the exercise, the video should be a youtube video or any external link for a video
- the client should be able to see which wourkout is already done for that week
- On the workout the costumer should be able also to see an option that if they click, show graph with the progress of the weight during the weeks

## About the training plan
- The training plan should have at least a name, decrtiption, start and end date and level, like beginner, intermediated, advanced, etc..
- 
- 
