# 6. Partial server logic

Date: 2020-06-01

## Context

The server logic often should be provided in parts - e.g. common authentication logic should be extracted into a 
reusable function.

## Decision

More power is given to `ServerEndpoint`. There are now three variants of the class: 

1. where the entire server logic is provided
2. where the server logic for the entire inputs defined so far is provided
3. where the server logic for some of the inputs defined so far is provided

In case of 2., the endpoint can be extended with further inputs/outputs, and further logic fragments (again for
the entire input defined so far) can be given.

It's not possible to combine 2. and 3. without an explosion of type parameters (of which there already are many).
Providing the server logic only for some inputs, while maintaining the capability of extending the input list, would
require a lot of type-level computations, and tracking a lot of intermediate variables.   